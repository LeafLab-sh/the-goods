package sh.leaflab.goods;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> CURRENCY_NAME = BUILDER
            .comment("The server-wide name of the currency.")
            .define("currencyName", "Credits");

    public static final ModConfigSpec.IntValue TRANSACTION_FEE_PERCENT = BUILDER
            .comment("Percentage fee charged on buys only (never sells), 0-100.")
            .defineInRange("transactionFeePercent", 0, 0, 100);

    // Denylisting an item that already has stock still allows that stock to be sold down to 0; it only blocks
    // new deposits from that point on.
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_DENY_LIST = BUILDER
            .comment("Items excluded from trading. Ignored if itemAllowList is non-empty. Empty by default.")
            .defineListAllowEmpty("itemDenyList", List.of(), () -> "", Config::validateItemName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_ALLOW_LIST = BUILDER
            .comment("If non-empty, only these items can be traded and itemDenyList is ignored. Empty by default.")
            .defineListAllowEmpty("itemAllowList", List.of(), () -> "", Config::validateItemName);

    public static final ModConfigSpec.BooleanValue AUDIT_LOG_ENABLED = BUILDER
            .comment("Enable audit logging of all currency transfers and admin actions.")
            .define("auditLog.enabled", true);

    public static final ModConfigSpec.IntValue AUDIT_LOG_MAX_ENTRIES = BUILDER
            .comment("Maximum number of audit log entries to retain (0 = unlimited).")
            .defineInRange("auditLog.maxEntries", 100, 0, 10000);

    static final ModConfigSpec SPEC = BUILDER.build();

    // Still fails hard on an invalid entry (crashes config loading) rather than silently correcting it away, but
    // logs a specific, readable reason first — Identifier.parse()'s own IdentifierException on malformed input is
    // accurate but terse, and "not a registered item" isn't distinguished from "not parseable" at all otherwise.
    private static boolean validateItemName(final Object obj) {
        if (!(obj instanceof String itemName)) {
            TheGoods.LOGGER.error("Invalid itemDenyList/itemAllowList entry (expected a string): {}", obj);
            throw new IllegalArgumentException("Invalid itemDenyList/itemAllowList entry (expected a string): " + obj);
        }
        Identifier id = Identifier.tryParse(itemName);
        if (id == null) {
            TheGoods.LOGGER.error("Invalid itemDenyList/itemAllowList entry: '{}' is not a valid item id", itemName);
            throw new IllegalArgumentException("Invalid itemDenyList/itemAllowList entry: '" + itemName + "' is not a valid item id");
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            TheGoods.LOGGER.error("Invalid itemDenyList/itemAllowList entry: '{}' is not a registered item", itemName);
            throw new IllegalArgumentException("Invalid itemDenyList/itemAllowList entry: '" + itemName + "' is not a registered item");
        }
        return true;
    }
}
