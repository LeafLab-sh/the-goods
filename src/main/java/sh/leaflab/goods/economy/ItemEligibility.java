package sh.leaflab.goods.economy;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
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
        List<? extends String> allowList = Config.ITEM_ALLOW_LIST.get();
        if (!allowList.isEmpty()) {
            return matchesAny(allowList, stack);
        }
        return !matchesAny(Config.ITEM_DENY_LIST.get(), stack);
    }

    // Config entries can be plain item ids ("minecraft:stick", "stick") or tag references ("#minecraft:planks",
    // "#c:foods"). Tag entries are checked via stack.is(tagKey) — any matching tag passes. Plain entries
    // are compared against the item's registry key as before. Entries are stored verbatim (see Config javadoc
    // on identifier normalization), so plain entries are re-parsed at each check.
    private static boolean matchesAny(List<? extends String> configList, ItemStack stack) {
        for (String entry : configList) {
            if (entry.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(entry.substring(1));
                if (tagId != null && stack.is(TagKey.create(Registries.ITEM, tagId))) {
                    return true;
                }
            } else {
                Identifier itemId = Identifier.tryParse(entry);
                if (itemId != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
