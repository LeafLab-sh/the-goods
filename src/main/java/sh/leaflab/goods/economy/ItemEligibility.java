package sh.leaflab.goods.economy;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import sh.leaflab.goods.Config;

// Hard rules (spec) that ItemAllowList cannot override: stackable items only, and only in default component
// state (checked via the component patch being empty, not by enumerating known component types — enumerating
// specific components would miss a future one and reopen the anvil-rename exploit this check exists to close).
public final class ItemEligibility {
    private ItemEligibility() {
    }

    public static boolean isEligible(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() <= 1 || !stack.isComponentsPatchEmpty()) {
            return false;
        }
        return isAllowedByConfig(stack);
    }

    private static boolean isAllowedByConfig(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        List<? extends String> allowList = Config.ITEM_ALLOW_LIST.get();
        if (!allowList.isEmpty()) {
            return containsItem(allowList, id);
        }
        return !containsItem(Config.ITEM_DENY_LIST.get(), id);
    }

    // Config entries are stored verbatim as the user typed them (e.g. "stick", not normalized to
    // "minecraft:stick"), so each entry must be re-parsed to a canonical Identifier before comparing — a plain
    // string comparison against the item's fully-qualified id would silently never match.
    private static boolean containsItem(List<? extends String> configList, Identifier id) {
        return configList.stream().anyMatch(entry -> id.equals(Identifier.tryParse(entry)));
    }
}
