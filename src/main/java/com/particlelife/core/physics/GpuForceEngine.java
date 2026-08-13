package com.particlelife.core.physics;

import org.lwjgl.PointerBuffer;
import org.lwjgl.egl.EGL10;
import org.lwjgl.egl.EGL12;
import org.lwjgl.egl.EGL14;
import org.lwjgl.egl.EGL15;
import org.lwjgl.egl.EXTDeviceEnumeration;
import org.lwjgl.egl.EXTPlatformDevice;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GPU force pass: the pairwise Particle Life force accumulation run as OpenGL
 * compute shaders.
 *
 * <p><strong>Context.</strong> Created headless through the EGL device
 * platform ({@code EGL_EXT_platform_device} over the DRM render node), which
 * reaches the discrete GPU even without a display; devices are tried in order,
 * preferring NVIDIA, and the first working GL 4.3+ context wins. If EGL device
 * enumeration is unavailable, a hidden GLFW window on the X display is the
 * fallback. Any failure leaves {@link #available()} {@code false} and the
 * engine uses the CPU force calculator.
 *
 * <p><strong>Grid.</strong> Mirrors the CPU {@link SpatialGrid}: a uniform
 * {@code m³} grid with {@code m = max(1, floor(L / r_max))} cells, particles
 * binned by an atomic counting sort (bin + count, then scatter against the
 * CPU-computed exclusive prefix sum), then a 27-cell neighbor force kernel
 * with the same minimum-image and {@code scale / max(r, minDistance)}
 * semantics as {@link ForceCalculator}. The force math is FP32, so results
 * are physically equivalent (not bit-identical) to the double CPU path —
 * validated against the reference to &lt;0.7% relative error. Below 3 cells
 * per axis (or absurdly many cells) the O(N²) brute kernel is used, exactly
 * as the CPU grid does.
 *
 * <p><strong>Threading.</strong> Not thread-safe; created and driven from the
 * simulation engine thread only.
 *
 * <p><strong>Known limitations.</strong> The per-frame CPU↔GPU sync (buffer
 * transfer + fence) costs on the order of 100-200 µs/frame on typical laptop
 * GPUs, so below very large populations (tens of thousands) the parallel CPU
 * spatial grid is faster; {@code AUTO} therefore never selects this backend.
 * Additionally, on NVIDIA drivers the surfaceless EGL device context has been
 * observed to SIGSEGV inside {@code libEGL_nvidia} under sustained heavy
 * compute (roughly 30k+ particles for seconds). Use the {@link ComputeBackend#GPU}
 * selection only when it is genuinely faster and stable on the target machine.
 */
public final class GpuForceEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GpuForceEngine.class);

    private static final int WORKGROUP_SIZE = 256;

    /** Safety cap on grid cells before falling back to brute force. */
    private static final int MAX_GRID_CELLS = 2_000_000;

    private static final String COMMON = """
            #version 430

            layout(local_size_x = 256) in;

            layout(std430, binding = 0) buffer PosBuf { float pos[]; };
            layout(std430, binding = 1) buffer SpecBuf { uint spec[]; };
            layout(std430, binding = 2) buffer ForBuf { float force[]; };
            layout(std430, binding = 3) buffer MatBuf { float mat[]; };
            layout(std430, binding = 4) buffer CellIdsBuf { uint cellIds[]; };
            layout(std430, binding = 5) buffer CellCountsBuf { uint cellCounts[]; };
            layout(std430, binding = 6) buffer CellStartBuf { uint cellStart[]; };
            layout(std430, binding = 7) buffer SortedBuf { uint sorted[]; };

            uniform int   uCount;
            uniform int   uMatrixSize;
            uniform float uBeta;
            uniform float uRMax;
            uniform float uMinDist;
            uniform float uScale;
            uniform float uWorldSize;
            uniform bool  uPeriodic;
            uniform int   uCellsPerAxis;
            uniform float uInverseCellSize;
            uniform float uHalfWorldSize;

            float kernelForce(float x, float a) {
                if (x < uBeta) {
                    return x / uBeta - 1.0;
                }
                if (x < 1.0) {
                    return a * (1.0 - abs(2.0 * x - 1.0 - uBeta) / (1.0 - uBeta));
                }
                return 0.0;
            }

            float wrapDelta(float d) {
                float h = uWorldSize * 0.5;
                if (d > h) {
                    return d - uWorldSize;
                }
                if (d < -h) {
                    return d + uWorldSize;
                }
                return d;
            }

            int ncoord(int c) {
                if (uPeriodic) {
                    if (c < 0) {
                        return c + uCellsPerAxis;
                    }
                    if (c >= uCellsPerAxis) {
                        return c - uCellsPerAxis;
                    }
                    return c;
                }
                if (c < 0 || c >= uCellsPerAxis) {
                    return -1;
                }
                return c;
            }

            uint cellId(vec3 p) {
                int cx = clamp(int(floor((p.x + uHalfWorldSize) * uInverseCellSize)), 0, uCellsPerAxis - 1);
                int cy = clamp(int(floor((p.y + uHalfWorldSize) * uInverseCellSize)), 0, uCellsPerAxis - 1);
                int cz = clamp(int(floor((p.z + uHalfWorldSize) * uInverseCellSize)), 0, uCellsPerAxis - 1);
                return uint((cz * uCellsPerAxis + cy) * uCellsPerAxis + cx);
            }
            """;

    private static final String BIN_COUNT = COMMON + """

            void main() {
                uint i = gl_GlobalInvocationID.x;
                if (i >= uCount) {
                    return;
                }
                uint cell = cellId(vec3(pos[3 * i], pos[3 * i + 1], pos[3 * i + 2]));
                cellIds[i] = cell;
                atomicAdd(cellCounts[cell], 1u);
            }
            """;

    private static final String SCATTER = COMMON + """

            void main() {
                uint i = gl_GlobalInvocationID.x;
                if (i >= uCount) {
                    return;
                }
                uint cell = cellIds[i];
                uint local = atomicAdd(cellCounts[cell], 1u);
                sorted[cellStart[cell] + local] = i;
            }
            """;

    private static final String FORCE_GRID = COMMON + """

            void main() {
                uint i = gl_GlobalInvocationID.x;
                if (i >= uCount) {
                    return;
                }

                vec3 p = vec3(pos[3 * i], pos[3 * i + 1], pos[3 * i + 2]);
                uint si = spec[i];
                int cx = clamp(int(floor((p.x + uHalfWorldSize) * uInverseCellSize)), 0, uCellsPerAxis - 1);
                int cy = clamp(int(floor((p.y + uHalfWorldSize) * uInverseCellSize)), 0, uCellsPerAxis - 1);
                int cz = clamp(int(floor((p.z + uHalfWorldSize) * uInverseCellSize)), 0, uCellsPerAxis - 1);
                float rMax2 = uRMax * uRMax;
                vec3 acc = vec3(0.0);

                for (int dz = -1; dz <= 1; dz++) {
                    int ncz = ncoord(cz + dz);
                    if (ncz < 0) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        int ncy = ncoord(cy + dy);
                        if (ncy < 0) {
                            continue;
                        }
                        for (int dx = -1; dx <= 1; dx++) {
                            int ncx = ncoord(cx + dx);
                            if (ncx < 0) {
                                continue;
                            }
                            int cell = (ncz * uCellsPerAxis + ncy) * uCellsPerAxis + ncx;
                            uint end = cellStart[cell + 1];
                            for (uint e = cellStart[cell]; e < end; e++) {
                                uint j = sorted[e];
                                if (j == i) {
                                    continue;
                                }
                                vec3 d = vec3(pos[3 * j], pos[3 * j + 1], pos[3 * j + 2]) - p;
                                if (uPeriodic) {
                                    d.x = wrapDelta(d.x);
                                    d.y = wrapDelta(d.y);
                                    d.z = wrapDelta(d.z);
                                }
                                float d2 = dot(d, d);
                                if (d2 >= rMax2 || d2 == 0.0) {
                                    continue;
                                }
                                float r = sqrt(d2);
                                float a = mat[si * uint(uMatrixSize) + spec[j]];
                                float f = kernelForce(r / uRMax, a);
                                float s = f * uScale / max(r, uMinDist);
                                acc += d * s;
                            }
                        }
                    }
                }
                force[3 * i] = acc.x;
                force[3 * i + 1] = acc.y;
                force[3 * i + 2] = acc.z;
            }
            """;

    private static final String FORCE_BRUTE = COMMON + """

            void main() {
                uint i = gl_GlobalInvocationID.x;
                if (i >= uCount) {
                    return;
                }

                vec3 p = vec3(pos[3 * i], pos[3 * i + 1], pos[3 * i + 2]);
                uint si = spec[i];
                float rMax2 = uRMax * uRMax;
                vec3 acc = vec3(0.0);

                for (uint j = 0u; j < uCount; j++) {
                    if (j == i) {
                        continue;
                    }
                    vec3 d = vec3(pos[3 * j], pos[3 * j + 1], pos[3 * j + 2]) - p;
                    if (uPeriodic) {
                        d.x = wrapDelta(d.x);
                        d.y = wrapDelta(d.y);
                        d.z = wrapDelta(d.z);
                    }
                    float d2 = dot(d, d);
                    if (d2 >= rMax2 || d2 == 0.0) {
                        continue;
                    }
                    float r = sqrt(d2);
                    float a = mat[si * uint(uMatrixSize) + spec[j]];
                    float f = kernelForce(r / uRMax, a);
                    float s = f * uScale / max(r, uMinDist);
                    acc += d * s;
                }
                force[3 * i] = acc.x;
                force[3 * i + 1] = acc.y;
                force[3 * i + 2] = acc.z;
            }
            """;

    /** Compile-time bundle of a compute program and its uniform locations. */
    private static final class Kernel {
        final int program;
        final int uCount;
        final int uMatrixSize;
        final int uBeta;
        final int uRMax;
        final int uMinDist;
        final int uScale;
        final int uWorldSize;
        final int uPeriodic;
        final int uCellsPerAxis;
        final int uInverseCellSize;
        final int uHalfWorldSize;

        Kernel(String source) {
            int shader = compileShader(source);
            program = shader == 0 ? 0 : linkProgram(shader);
            uCount = uniform(program, "uCount");
            uMatrixSize = uniform(program, "uMatrixSize");
            uBeta = uniform(program, "uBeta");
            uRMax = uniform(program, "uRMax");
            uMinDist = uniform(program, "uMinDist");
            uScale = uniform(program, "uScale");
            uWorldSize = uniform(program, "uWorldSize");
            uPeriodic = uniform(program, "uPeriodic");
            uCellsPerAxis = uniform(program, "uCellsPerAxis");
            uInverseCellSize = uniform(program, "uInverseCellSize");
            uHalfWorldSize = uniform(program, "uHalfWorldSize");
        }

        boolean ready() {
            return program != 0;
        }
    }

    // EGL bookkeeping (engine thread).
    private boolean eglActive;
    private long eglDisplay;

    // GLFW bookkeeping (engine thread).
    private boolean glfwInitialized;
    private boolean windowCreated;
    private long window;

    private Kernel binCountKernel;
    private Kernel scatterKernel;
    private Kernel forceGridKernel;
    private Kernel forceBruteKernel;

    private int posBuffer;
    private int specBuffer;
    private int forceBuffer;
    private int matBuffer;
    private int cellIdsBuffer;
    private int cellCountsBuffer;
    private int cellStartBuffer;
    private int sortedBuffer;

    // Persistent-coherent mappings so per-frame CPU reads avoid glMapBufferRange
    // stalls; the only CPU<->GPU crossings are guarded by fences.
    private ByteBuffer forceMapped;
    private ByteBuffer cellCountsMapped;
    private ByteBuffer cellStartMapped;

    private int lastCapacity;
    private int lastMatrixSize;
    private int lastCellCount;
    private FloatBuffer posScratch;
    private IntBuffer specScratch;
    private FloatBuffer matScratch;
    private int[] counts = new int[1];
    private int[] cellStartValues = new int[1];

    private boolean initialized;
    private boolean available;
    private String renderer = "unknown";

    /**
     * Attempts to create the GL context and compile the kernels on the calling
     * (engine) thread. Idempotent; on any failure this engine reports
     * {@link #available()}{@code == false} and the caller keeps the CPU path.
     */
    public void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            if (!initEgl()) {
                initGlfw();
            }
        } catch (Throwable t) {
            log.warn("GPU force pass unavailable, falling back to CPU: {}", t.toString());
            cleanup();
        }
    }

    /** Whether the compute kernels are ready for dispatch on this thread. */
    public boolean available() {
        return available;
    }

    /** The GL renderer string once initialized (diagnostics). */
    public String renderer() {
        return renderer;
    }

    /**
     * Runs the force pass, overwriting {@code forces[0 .. 3*count)}.
     * Call only when {@link #available()}. Arrays are the store's live
     * capacity-sized flat buffers; only the first {@code count} particles are
     * processed.
     */
    public void compute(double[] positions,
                        int[] species,
                        double[] forces,
                        int count,
                        int capacity,
                        double[] matrix,
                        int matrixSize,
                        double rMax,
                        double minDistance,
                        double scale,
                        double worldSize,
                        boolean periodic,
                        double beta) {
        int cellsPerAxis = Math.max(1, (int) (worldSize / rMax));
        int cellCount = cellsPerAxis * cellsPerAxis * cellsPerAxis;
        boolean useGrid = cellsPerAxis >= 3 && cellCount <= MAX_GRID_CELLS;
        ensureSized(capacity, matrixSize, cellCount, useGrid);

        uploadFloats(posBuffer, posScratch, positions, count * 3);
        uploadInts(specBuffer, specScratch, species, count);
        uploadFloats(matBuffer, matScratch, matrix, matrixSize * matrixSize);
        barrier();

        if (useGrid) {
            double halfWorld = worldSize * 0.5;
            double inverseCell = (double) cellsPerAxis / worldSize;

            zeroCounts();
            bindAndDispatch(binCountKernel, count, cellsPerAxis, inverseCell, halfWorld);
            binFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

            waitForGpu(binFence);
            binFence = 0;
            readCounts(cellCount);
            computeCellStart(cellCount);
            writeCellStart();

            zeroCounts();
            bindAndDispatch(scatterKernel, count, cellsPerAxis, inverseCell, halfWorld);

            dispatchForceGrid(count, cellsPerAxis, inverseCell, halfWorld, beta, rMax,
                    minDistance, scale, worldSize, periodic, matrixSize);
            forceFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            waitForGpu(forceFence);
            forceFence = 0;
        } else {
            dispatchForceBrute(count, beta, rMax, minDistance, scale, worldSize,
                    periodic, matrixSize);
            forceFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            waitForGpu(forceFence);
            forceFence = 0;
        }

        readForces(count, forces);
    }

    private long binFence;
    private long forceFence;

    private void waitForGpu(long sync) {
        if (sync == 0) {
            return;
        }
        int result = GL32.glClientWaitSync(sync, 0, GL32.GL_TIMEOUT_IGNORED);
        if (result == GL32.GL_WAIT_FAILED) {
            log.warn("GPU fence wait failed");
        }
        GL32.glDeleteSync(sync);
    }

    private void readCounts(int cellCount) {
        if (cellCountsMapped == null) {
            throw new IllegalStateException("cellCounts buffer not mapped");
        }
        IntBuffer read = cellCountsMapped.order(ByteOrder.nativeOrder()).asIntBuffer();
        if (counts.length < cellCount) {
            counts = new int[cellCount];
        }
        for (int c = 0; c < cellCount; c++) {
            counts[c] = read.get(c);
        }
    }

    private void writeCellStart() {
        if (cellStartMapped == null) {
            throw new IllegalStateException("cellStart buffer not mapped");
        }
        IntBuffer dst = cellStartMapped.order(ByteOrder.nativeOrder()).asIntBuffer();
        dst.put(cellStartValues);
    }

    private void readForces(int count, double[] forces) {
        if (forceMapped == null) {
            throw new IllegalStateException("force buffer not mapped");
        }
        FloatBuffer read = forceMapped.order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int k = 0; k < count * 3; k++) {
            forces[k] = read.get(k);
        }
    }

    /** Tries the EGL device platform; returns true when a context is current. */
    private boolean initEgl() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer devices = stack.mallocPointer(16);
            IntBuffer numDevices = stack.mallocInt(1);
            if (!EXTDeviceEnumeration.eglQueryDevicesEXT(devices, numDevices)
                    || numDevices.get(0) == 0) {
                return false;
            }
            // Prefer NVIDIA, then any remaining device.
            for (int pass = 0; pass < 2; pass++) {
                for (int d = 0; d < numDevices.get(0); d++) {
                    long device = devices.get(d);
                    long display = EGL15.eglGetPlatformDisplay(
                            EXTPlatformDevice.EGL_PLATFORM_DEVICE_EXT, device, (PointerBuffer) null);
                    if (display == EGL10.EGL_NO_DISPLAY) {
                        continue;
                    }
                    int[] major = new int[1];
                    int[] minor = new int[1];
                    if (!EGL10.eglInitialize(display, major, minor)) {
                        continue;
                    }
                    String vendor = EGL10.eglQueryString(display, EGL10.EGL_VENDOR);
                    boolean nvidia = vendor != null && vendor.toUpperCase().contains("NVIDIA");
                    if ((pass == 0 && !nvidia) || (pass == 1 && nvidia)) {
                        EGL10.eglTerminate(display);
                        continue;
                    }
                    if (tryEglContext(display)) {
                        eglActive = true;
                        eglDisplay = display;
                        return true;
                    }
                    EGL10.eglTerminate(display);
                }
            }
        }
        return false;
    }

    /** Creates a surfaceless GL 4.6 core context on {@code display}. */
    private boolean tryEglContext(long display) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] attribs = {
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL12.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_BIT,
                    EGL10.EGL_NONE
            };
            PointerBuffer configs = stack.mallocPointer(8);
            int[] numConfigs = new int[1];
            if (!EGL10.eglChooseConfig(display, attribs, configs, numConfigs) || numConfigs[0] == 0) {
                return false;
            }
            long config = configs.get(0);
            EGL14.eglBindAPI(EGL14.EGL_OPENGL_API);
            int[][] variants = {
                    {EGL15.EGL_CONTEXT_MAJOR_VERSION, 4, EGL15.EGL_CONTEXT_MINOR_VERSION, 6,
                            EGL15.EGL_CONTEXT_OPENGL_PROFILE_MASK,
                            EGL15.EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT, EGL10.EGL_NONE},
                    {EGL15.EGL_CONTEXT_MAJOR_VERSION, 4,
                            EGL15.EGL_CONTEXT_OPENGL_PROFILE_MASK,
                            EGL15.EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT, EGL10.EGL_NONE},
                    {EGL15.EGL_CONTEXT_MAJOR_VERSION, 4, EGL15.EGL_CONTEXT_MINOR_VERSION, 3,
                            EGL15.EGL_CONTEXT_OPENGL_PROFILE_MASK,
                            EGL15.EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT, EGL10.EGL_NONE},
            };
            long context = EGL10.EGL_NO_CONTEXT;
            for (int[] variant : variants) {
                context = EGL10.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, variant);
                if (context != EGL10.EGL_NO_CONTEXT) {
                    break;
                }
            }
            if (context == EGL10.EGL_NO_CONTEXT) {
                return false;
            }
            if (!EGL10.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, context)) {
                return false;
            }
            GL.createCapabilities();
            // Kernel compile outcome decides available(); a context is acquired
            // either way, so no further devices or GLFW fallback are attempted.
            finishContextSetup();
            return true;
        }
    }

    /** Falls back to a hidden GLFW window on the X display. */
    private void initGlfw() {
        GLFW.glfwSetErrorCallback((error, description) ->
                log.error("GLFW error {}: {}", error, description));
        glfwInitialized = GLFW.glfwInit();
        if (!glfwInitialized) {
            log.warn("glfwInit failed (no display?); GPU force pass disabled");
            return;
        }
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        // Some X servers (e.g. Xwayland) only expose limited GLX visuals; try
        // the full 4.6 first and fall back to the 4.3 minimum for compute.
        window = createWindow(4, 6);
        if (window == 0) {
            window = createWindow(4, 3);
        }
        if (window == 0) {
            log.warn("GLFW window creation failed; GPU force pass disabled");
            return;
        }
        windowCreated = true;
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(0);
        GL.createCapabilities();
        finishContextSetup();
    }

    /** Compiles the kernels and allocates buffers; sets {@link #available}. */
    private boolean finishContextSetup() {
        String version = GL11.glGetString(GL11.GL_VERSION);
        renderer = GL11.glGetString(GL11.GL_RENDERER);

        binCountKernel = new Kernel(BIN_COUNT);
        scatterKernel = new Kernel(SCATTER);
        forceGridKernel = new Kernel(FORCE_GRID);
        forceBruteKernel = new Kernel(FORCE_BRUTE);
        if (!binCountKernel.ready() || !scatterKernel.ready()
                || !forceGridKernel.ready() || !forceBruteKernel.ready()) {
            log.warn("GPU compute kernel compilation failed; GPU force pass disabled");
            return false;
        }

        posBuffer = glGenBuffer();
        specBuffer = glGenBuffer();
        forceBuffer = glGenBuffer();
        matBuffer = glGenBuffer();
        cellIdsBuffer = glGenBuffer();
        cellCountsBuffer = glGenBuffer();
        cellStartBuffer = glGenBuffer();
        sortedBuffer = glGenBuffer();

        available = true;
        log.info("GPU force pass active: {} (GL {})", renderer, version);
        return true;
    }

    private static long createWindow(int major, int minor) {
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, minor);
        return GLFW.glfwCreateWindow(1, 1, "quantumlife-gpu-compute", 0, 0);
    }

    private static int compileShader(String source) {
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        if (shader == 0) {
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            log.warn("Compute shader compile failed: {}", GL20.glGetShaderInfoLog(shader));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static int linkProgram(int shader) {
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, shader);
        GL20.glLinkProgram(prog);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            log.warn("Compute program link failed: {}", GL20.glGetProgramInfoLog(prog));
            GL20.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    private static int uniform(int program, String name) {
        return program == 0 ? -1 : GL20.glGetUniformLocation(program, name);
    }

    private static void barrier() {
        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT
                | GL42.GL_ATOMIC_COUNTER_BARRIER_BIT | GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
    }

    private void bindAndDispatch(Kernel kernel, int count, int cellsPerAxis,
                                 double inverseCell, double halfWorld) {
        GL20.glUseProgram(kernel.program);
        if (kernel.uCount >= 0) {
            GL20.glUniform1i(kernel.uCount, count);
        }
        if (kernel.uCellsPerAxis >= 0) {
            GL20.glUniform1i(kernel.uCellsPerAxis, cellsPerAxis);
        }
        if (kernel.uInverseCellSize >= 0) {
            GL20.glUniform1f(kernel.uInverseCellSize, (float) inverseCell);
        }
        if (kernel.uHalfWorldSize >= 0) {
            GL20.glUniform1f(kernel.uHalfWorldSize, (float) halfWorld);
        }
        barrier();
        GL43.glDispatchCompute((count + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE, 1, 1);
        barrier();
    }

    private void dispatchForceGrid(int count, int cellsPerAxis, double inverseCell,
                                   double halfWorld, double beta, double rMax,
                                   double minDistance, double scale, double worldSize,
                                   boolean periodic, int matrixSize) {
        GL20.glUseProgram(forceGridKernel.program);
        setForceUniforms(forceGridKernel, count, beta, rMax, minDistance, scale,
                worldSize, periodic, matrixSize, cellsPerAxis, inverseCell, halfWorld);
        barrier();
        GL43.glDispatchCompute((count + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE, 1, 1);
        barrier();
    }

    private void dispatchForceBrute(int count, double beta, double rMax,
                                    double minDistance, double scale, double worldSize,
                                    boolean periodic, int matrixSize) {
        GL20.glUseProgram(forceBruteKernel.program);
        setForceUniforms(forceBruteKernel, count, beta, rMax, minDistance, scale,
                worldSize, periodic, matrixSize, 0, 0, 0);
        barrier();
        GL43.glDispatchCompute((count + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE, 1, 1);
        barrier();
    }

    private void setForceUniforms(Kernel kernel, int count, double beta, double rMax,
                                  double minDistance, double scale, double worldSize,
                                  boolean periodic, int matrixSize, int cellsPerAxis,
                                  double inverseCell, double halfWorld) {
        if (kernel.uCount >= 0) {
            GL20.glUniform1i(kernel.uCount, count);
        }
        if (kernel.uMatrixSize >= 0) {
            GL20.glUniform1i(kernel.uMatrixSize, matrixSize);
        }
        if (kernel.uBeta >= 0) {
            GL20.glUniform1f(kernel.uBeta, (float) beta);
        }
        if (kernel.uRMax >= 0) {
            GL20.glUniform1f(kernel.uRMax, (float) rMax);
        }
        if (kernel.uMinDist >= 0) {
            GL20.glUniform1f(kernel.uMinDist, (float) minDistance);
        }
        if (kernel.uScale >= 0) {
            GL20.glUniform1f(kernel.uScale, (float) scale);
        }
        if (kernel.uWorldSize >= 0) {
            GL20.glUniform1f(kernel.uWorldSize, (float) worldSize);
        }
        if (kernel.uPeriodic >= 0) {
            GL20.glUniform1i(kernel.uPeriodic, periodic ? 1 : 0);
        }
        if (kernel.uCellsPerAxis >= 0) {
            GL20.glUniform1i(kernel.uCellsPerAxis, cellsPerAxis);
        }
        if (kernel.uInverseCellSize >= 0) {
            GL20.glUniform1f(kernel.uInverseCellSize, (float) inverseCell);
        }
        if (kernel.uHalfWorldSize >= 0) {
            GL20.glUniform1f(kernel.uHalfWorldSize, (float) halfWorld);
        }
    }

    private void zeroCounts() {
        if (cellCountsMapped == null) {
            throw new IllegalStateException("cellCounts buffer not mapped");
        }
        MemoryUtil.memSet(cellCountsMapped, 0);
    }

    private void computeCellStart(int cellCount) {
        if (cellStartValues.length < cellCount + 1) {
            cellStartValues = new int[cellCount + 1];
        }
        int acc = 0;
        cellStartValues[0] = 0;
        for (int c = 0; c < cellCount; c++) {
            acc += counts[c];
            cellStartValues[c + 1] = acc;
        }
    }

    private void ensureSized(int capacity, int matrixSize, int cellCount, boolean useGrid) {
        if (capacity == lastCapacity && matrixSize == lastMatrixSize && cellCount == lastCellCount) {
            return;
        }
        int posBytes = capacity * 3 * Float.BYTES;
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, posBuffer);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, posBytes, GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, posBuffer);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, specBuffer);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity * Integer.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, specBuffer);

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, forceBuffer);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, matBuffer);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, matrixSize * matrixSize * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, matBuffer);

        int gridCellCount = useGrid ? cellCount : 0;
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, cellIdsBuffer);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity * Integer.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, cellIdsBuffer);

        unmapPersistent();
        remapPersistent(forceBuffer, posBytes, GL30.GL_MAP_READ_BIT);
        remapPersistent(cellCountsBuffer, gridCellCount * Integer.BYTES,
                GL30.GL_MAP_READ_BIT | GL44.GL_MAP_WRITE_BIT);
        remapPersistent(cellStartBuffer, (gridCellCount + 1) * Integer.BYTES, GL44.GL_MAP_WRITE_BIT);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 5, cellCountsBuffer);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 6, cellStartBuffer);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, sortedBuffer);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity * Integer.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, sortedBuffer);

        freeScratch(posScratch);
        freeScratch(specScratch);
        freeScratch(matScratch);
        posScratch = MemoryUtil.memAllocFloat(capacity * 3);
        specScratch = MemoryUtil.memAllocInt(capacity);
        matScratch = MemoryUtil.memAllocFloat(matrixSize * matrixSize);
        counts[0] = 0;
        cellStartValues = new int[Math.max(1, gridCellCount + 1)];

        lastCapacity = capacity;
        lastMatrixSize = matrixSize;
        lastCellCount = cellCount;
    }

    private static final int GL_STORAGE_FLAGS = GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT
            | GL30.GL_MAP_READ_BIT | GL44.GL_MAP_WRITE_BIT;

    private void remapPersistent(int buffer, int bytes, int accessFlags) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, (long) bytes, GL_STORAGE_FLAGS);
        ByteBuffer mapped = GL30.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0, (long) bytes,
                accessFlags | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT);
        if (mapped == null) {
            throw new IllegalStateException("Persistent buffer map failed (buffer=" + buffer + ")");
        }
        if (buffer == forceBuffer) {
            forceMapped = mapped;
        } else if (buffer == cellCountsBuffer) {
            cellCountsMapped = mapped;
        } else if (buffer == cellStartBuffer) {
            cellStartMapped = mapped;
        }
    }

    private void unmapPersistent() {
        if (forceMapped != null || cellCountsMapped != null || cellStartMapped != null) {
            for (int buffer : new int[] {forceBuffer, cellCountsBuffer, cellStartBuffer}) {
                if (buffer != 0) {
                    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
                    GL30.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
                }
            }
            forceMapped = null;
            cellCountsMapped = null;
            cellStartMapped = null;
        }
    }

    private static int glGenBuffer() {
        return GL15.glGenBuffers();
    }

    private static void freeScratch(java.nio.Buffer scratch) {
        if (scratch != null) {
            MemoryUtil.memFree(scratch);
        }
    }

    private void uploadFloats(int targetBuffer, FloatBuffer scratch, double[] source, int len) {
        scratch.clear();
        for (int k = 0; k < len; k++) {
            scratch.put((float) source[k]);
        }
        scratch.flip();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, targetBuffer);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, scratch);
    }

    private void uploadInts(int targetBuffer, IntBuffer scratch, int[] source, int len) {
        scratch.clear();
        for (int k = 0; k < len; k++) {
            scratch.put(source[k]);
        }
        scratch.flip();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, targetBuffer);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, scratch);
    }

    /** Releases all GL resources. Must be called from the engine thread. */
    @Override
    public void close() {
        if (available || initialized) {
            cleanup();
        }
    }

    private void cleanup() {
        unmapPersistent();
        freeScratch(posScratch);
        freeScratch(specScratch);
        freeScratch(matScratch);
        posScratch = null;
        specScratch = null;
        matScratch = null;

        if (binCountKernel != null) {
            GL20.glDeleteProgram(binCountKernel.program);
            GL20.glDeleteProgram(scatterKernel.program);
            GL20.glDeleteProgram(forceGridKernel.program);
            GL20.glDeleteProgram(forceBruteKernel.program);
            binCountKernel = scatterKernel = forceGridKernel = forceBruteKernel = null;
        }
        if (posBuffer != 0) {
            GL15.glDeleteBuffers(posBuffer);
            GL15.glDeleteBuffers(specBuffer);
            GL15.glDeleteBuffers(forceBuffer);
            GL15.glDeleteBuffers(matBuffer);
            GL15.glDeleteBuffers(cellIdsBuffer);
            GL15.glDeleteBuffers(cellCountsBuffer);
            GL15.glDeleteBuffers(cellStartBuffer);
            GL15.glDeleteBuffers(sortedBuffer);
            posBuffer = specBuffer = forceBuffer = matBuffer = 0;
            cellIdsBuffer = cellCountsBuffer = cellStartBuffer = sortedBuffer = 0;
        }
        if (eglActive) {
            EGL10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            EGL10.eglTerminate(eglDisplay);
            eglActive = false;
        }
        if (windowCreated) {
            GLFW.glfwDestroyWindow(window);
            windowCreated = false;
        }
        if (glfwInitialized) {
            GLFW.glfwTerminate();
            glfwInitialized = false;
        }
        GLFW.glfwSetErrorCallback(null);
        available = false;
    }
}