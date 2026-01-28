package rsp.compositions.contract;

import rsp.compositions.composition.Slot;

import java.util.List;
import java.util.Map;

/**
 * SlotUtils - Generic utilities for working with contract slots.
 * <p>
 * Provides placement-agnostic helpers for finding which slot a contract is in.
 * No knowledge of specific operations or application logic - purely generic framework utilities.
 */
public class SlotUtils {

    /**
     * Find the slot where a contract is currently placed.
     * <p>
     * Generic - no knowledge of specific slots or operations.
     * Searches through Scene.activeContractsBySlot to find the contract.
     *
     * @param contractClass The contract class to find
     * @param scene The current scene
     * @return The slot where the contract is placed, or Slot.PRIMARY if not found
     */
    public static Slot findSlot(Class<? extends ViewContract> contractClass, Scene scene) {
        if (contractClass == null || scene == null) {
            return Slot.PRIMARY;  // Default fallback
        }

        for (Map.Entry<Slot, List<Scene.ActiveContract>> entry : scene.activeContractsBySlot().entrySet()) {
            for (Scene.ActiveContract active : entry.getValue()) {
                if (active.contractClass().equals(contractClass)) {
                    return entry.getKey();
                }
            }
        }
        return Slot.PRIMARY;  // Default fallback
    }

    /**
     * Check if contract is in overlay slot.
     * <p>
     * Convenience wrapper - still generic.
     *
     * @param contractClass The contract class to check
     * @param scene The current scene
     * @return true if contract is in OVERLAY slot, false otherwise
     */
    public static boolean isInOverlay(Class<? extends ViewContract> contractClass, Scene scene) {
        return findSlot(contractClass, scene) == Slot.OVERLAY;
    }
}
