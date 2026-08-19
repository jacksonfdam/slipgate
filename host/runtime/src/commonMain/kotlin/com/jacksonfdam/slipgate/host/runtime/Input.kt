package com.jacksonfdam.slipgate.host.runtime

import kotlin.jvm.JvmInline

/** A normalised two-axis input, each component in -1f..1f. */
public data class Axis2(
    val x: Float = 0f,
    val y: Float = 0f,
) {
    public companion object {
        public val Zero: Axis2 = Axis2()
    }
}

/**
 * Actions every supported engine understands. Engine-specific controls travel in
 * [InputFrame.extensions] rather than growing this set.
 */
public enum class GateAction {
    Fire,
    Use,
    Jump,
    Crouch,
    NextWeapon,
    PreviousWeapon,
    Map,
    Menu,

    /** Accepts the highlighted choice in an engine's own menus, the way Enter does. */
    Confirm,
    ;

    public val bit: Int
        get() = 1 shl ordinal
}

/** Set of actions held down during one frame. */
@JvmInline
public value class ActionSet(
    public val mask: Int = 0,
) {
    public operator fun contains(action: GateAction): Boolean = mask and action.bit != 0

    public operator fun plus(action: GateAction): ActionSet = ActionSet(mask or action.bit)

    public operator fun minus(action: GateAction): ActionSet = ActionSet(mask and action.bit.inv())

    public companion object {
        public val Empty: ActionSet = ActionSet()

        public fun of(vararg actions: GateAction): ActionSet =
            ActionSet(actions.fold(0) { mask, action -> mask or action.bit })
    }
}

/** One frame of input, already normalised by the platform's control layer. */
public data class InputFrame(
    val movement: Axis2 = Axis2.Zero,
    val look: Axis2 = Axis2.Zero,
    val actions: ActionSet = ActionSet.Empty,
    val extensions: Map<String, Float> = emptyMap(),
) {
    public companion object {
        public val Idle: InputFrame = InputFrame()
    }
}

/** An engine-specific control a gate wants surfaced beyond [GateAction]. */
public data class InputExtension(
    val key: String,
    val label: String,
)

/**
 * What a gate wants from the control layer, so a virtual gamepad can lay out exactly the
 * buttons that game uses and nothing more.
 */
public data class InputProfile(
    val actions: Set<GateAction>,
    val extensions: List<InputExtension> = emptyList(),
    val usesLookAxis: Boolean = false,
)
