package com.jacksonfdam.slipgate.host.backend.wasm

import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.runtime.value.NumberValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The question this answers is the one the whole engine port rests on: can Chasm run a module built
 * with wasm exceptions and wasm longjmp?
 *
 * The engines use setjmp and longjmp for their error paths, and Emscripten implements wasm longjmp
 * on top of the exception handling proposal. If Chasm cannot execute that, the port has no route
 * that does not involve Asyncify, which is a performance disaster on an interpreter.
 *
 * The probe is deliberately tiny and has no imports, so a failure here means the feature rather than
 * the environment. Its source is `tooling/engine-build/verification/longjmp_probe.c`.
 */
class ExceptionHandlingProbeTest {
    private fun probeBytes(): ByteArray {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/longjmp_probe.wasm")) {
                "the probe module is missing from test resources"
            }
        return stream.use { it.readBytes() }
    }

    @Test
    fun theProbeModuleLoads() {
        val bytes = probeBytes()

        assertTrue(bytes.size > 8, "the probe module is empty")
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals('a'.code.toByte(), bytes[1])

        val store = store()
        val module = module(bytes).expect("the probe module did not parse")
        instance(store, module, emptyList()).expect("the probe module did not instantiate")
    }

    @Test
    fun aPlainCallReturns() {
        val store = store()
        val module = module(probeBytes()).expect("the probe module did not parse")
        val instance = instance(store, module, emptyList()).expect("the probe module did not instantiate")

        val result =
            invoke(store, instance, "probe_add", listOf(NumberValue.I32(2), NumberValue.I32(3)))
                .expect("probe_add did not run")

        assertEquals(listOf(NumberValue.I32(5)), result)
    }

    @Test
    fun longjmpUnwindsAndReturnsItsValue() {
        val store = store()
        val module = module(probeBytes()).expect("the probe module did not parse")
        val instance = instance(store, module, emptyList()).expect("the probe module did not instantiate")

        val result =
            invoke(store, instance, "probe_longjmp", listOf(NumberValue.I32(42)))
                .expect("probe_longjmp did not run")

        assertEquals(listOf(NumberValue.I32(42)), result)
    }
}
