package com.denizenscript.denizen.nms.v1_20.helpers;

import com.denizenscript.denizen.nms.interfaces.ItemHelper;
import com.denizenscript.denizen.nms.util.PlayerProfile;
import com.denizenscript.denizen.nms.util.jnbt.CompoundTag;
import com.denizenscript.denizen.nms.util.jnbt.IntArrayTag;
import com.denizenscript.denizen.nms.util.jnbt.Tag;
import com.denizenscript.denizen.nms.v1_20.ReflectionMappingsInfo;
import com.denizenscript.denizen.nms.v1_20.impl.jnbt.CompoundTagImpl;
import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizen.utilities.FormattedTextHelper;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.ReflectionHelper;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.google.common.collect.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_20_R2.CraftServer;
import org.bukkit.craftbukkit.v1_20_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R2.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_20_R2.inventory.CraftInventoryPlayer;
import org.bukkit.craftbukkit.v1_20_R2.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R2.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v1_20_R2.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.v1_20_R2.util.CraftNamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.lang.reflect.Field;
import java.util.*;

public class ItemHelperImpl extends ItemHelper {

    public static net.minecraft.world.item.crafting.RecipeHolder<?> getNMSRecipe(NamespacedKey key) {
        ResourceLocation nmsKey = CraftNamespacedKey.toMinecraft(key);
        for (Object2ObjectLinkedOpenHashMap<ResourceLocation, net.minecraft.world.item.crafting.RecipeHolder<?>> recipeMap : ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager().recipes.values()) {
            net.minecraft.world.item.crafting.RecipeHolder<?> recipe = recipeMap.get(nmsKey);
            if (recipe != null) {
                return recipe;
            }
        }
        return null;
    }

    public void setMaxStackSize(Material material, int size) {
        try {
            ReflectionHelper.getFinalSetter(Material.class, "maxStack").invoke(material, size);
            ReflectionHelper.getFinalSetter(Item.class, ReflectionMappingsInfo.Item_maxStackSize).invoke(BuiltInRegistries.ITEM.get(CraftNamespacedKey.toMinecraft(material.getKey())), size);
        }
        catch (Throwable ex) {
            Debug.echoError(ex);
        }
    }

    @Override
    public Integer burnTime(Material material) {
        return AbstractFurnaceBlockEntity.getFuel().get(CraftMagicNumbers.getItem(material));
    }

    public static Field RECIPE_MANAGER_BY_NAME = ReflectionHelper.getFields(RecipeManager.class).get(ReflectionMappingsInfo.RecipeManager_byName, Map.class);

    @Override
    public void clearDenizenRecipes() {
        RecipeManager recipeManager = ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager();
        Map<ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>> byName;
        try {
            byName = (Map) RECIPE_MANAGER_BY_NAME.get(recipeManager);
        }
        catch (Throwable ex) {
            Debug.echoError(ex);
            return;
        }
        for (Object2ObjectLinkedOpenHashMap<ResourceLocation, net.minecraft.world.item.crafting.RecipeHolder<?>> recipeMap : recipeManager.recipes.values()) {
            for (ResourceLocation key : new ArrayList<>(recipeMap.keySet())) {
                if (key.getNamespace().equalsIgnoreCase("denizen")) {
                    recipeMap.remove(key);
                    byName.remove(key);
                }
            }
        }
    }

    @Override
    public void setShapedRecipeIngredient(ShapedRecipe recipe, char c, ItemStack[] item, boolean exact) {
        if (item.length == 1 && item[0].getType() == Material.AIR) {
            recipe.setIngredient(c, new RecipeChoice.MaterialChoice(Material.AIR));
        }
        else if (exact) {
            recipe.setIngredient(c, new RecipeChoice.ExactChoice(item));
        }
        else {
            Material[] mats = new Material[item.length];
            for (int i = 0; i < item.length; i++) {
                mats[i] = item[i].getType();
            }
            recipe.setIngredient(c, new RecipeChoice.MaterialChoice(mats));
        }
    }

    public static Ingredient itemArrayToRecipe(ItemStack[] items, boolean exact) {
        Ingredient.ItemValue[] stacks = new Ingredient.ItemValue[items.length];
        for (int i = 0; i < items.length; i++) {
            stacks[i] = new Ingredient.ItemValue(CraftItemStack.asNMSCopy(items[i]));
        }
        Ingredient itemRecipe = new Ingredient(Arrays.stream(stacks));
        itemRecipe.exact = exact;
        return itemRecipe;
    }

    @Override
    public void registerFurnaceRecipe(String keyName, String group, ItemStack result, ItemStack[] ingredient, float exp, int time, String type, boolean exact, String category) {
        ResourceLocation key = new ResourceLocation("denizen", keyName);
        Ingredient itemRecipe = itemArrayToRecipe(ingredient, exact);
        AbstractCookingRecipe recipe;
        CookingBookCategory categoryValue = category == null ? CookingBookCategory.MISC : CookingBookCategory.valueOf(CoreUtilities.toUpperCase(category));
        if (type.equalsIgnoreCase("smoker")) {
            recipe = new SmokingRecipe(group, categoryValue, itemRecipe, CraftItemStack.asNMSCopy(result), exp, time);
        }
        else if (type.equalsIgnoreCase("blast")) {
            recipe = new BlastingRecipe(group, categoryValue, itemRecipe, CraftItemStack.asNMSCopy(result), exp, time);
        }
        else if (type.equalsIgnoreCase("campfire")) {
            recipe = new CampfireCookingRecipe(group, categoryValue, itemRecipe, CraftItemStack.asNMSCopy(result), exp, time);
        }
        else {
            recipe = new SmeltingRecipe(group, categoryValue, itemRecipe, CraftItemStack.asNMSCopy(result), exp, time);
        }
        RecipeHolder<AbstractCookingRecipe> holder = new RecipeHolder<>(key, recipe);
        ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager().addRecipe(holder);
    }

    @Override
    public void registerStonecuttingRecipe(String keyName, String group, ItemStack result, ItemStack[] ingredient, boolean exact) {
        ResourceLocation key = new ResourceLocation("denizen", keyName);
        Ingredient itemRecipe = itemArrayToRecipe(ingredient, exact);
        StonecutterRecipe recipe = new StonecutterRecipe(group, itemRecipe, CraftItemStack.asNMSCopy(result));
        RecipeHolder<StonecutterRecipe> holder = new RecipeHolder<>(key, recipe);
        ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager().addRecipe(holder);
    }

    @Override
    public void registerSmithingRecipe(String keyName, ItemStack result, ItemStack[] baseItem, boolean baseExact, ItemStack[] upgradeItem, boolean upgradeExact, ItemStack[] templateItem, boolean templateExact) {
        ResourceLocation key = new ResourceLocation("denizen", keyName);
        Ingredient templateItemRecipe = itemArrayToRecipe(templateItem, templateExact);
        Ingredient baseItemRecipe = itemArrayToRecipe(baseItem, baseExact);
        Ingredient upgradeItemRecipe = itemArrayToRecipe(upgradeItem, upgradeExact);
        SmithingTransformRecipe recipe = new SmithingTransformRecipe(templateItemRecipe, baseItemRecipe, upgradeItemRecipe, CraftItemStack.asNMSCopy(result));
        RecipeHolder<SmithingTransformRecipe> holder = new RecipeHolder<>(key, recipe);
        ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager().addRecipe(holder);
    }

    @Override
    public void registerShapelessRecipe(String keyName, String group, ItemStack result, List<ItemStack[]> ingredients, boolean[] exact, String category) {
        ResourceLocation key = new ResourceLocation("denizen", keyName);
        ArrayList<Ingredient> ingredientList = new ArrayList<>();
        CraftingBookCategory categoryValue = category == null ? CraftingBookCategory.MISC : CraftingBookCategory.valueOf(CoreUtilities.toUpperCase(category));
        for (int i = 0; i < ingredients.size(); i++) {
            ingredientList.add(itemArrayToRecipe(ingredients.get(i), exact[i]));
        }
        // TODO: 1.19.3: Add support for choosing a CraftingBookCategory
        ShapelessRecipe recipe = new ShapelessRecipe(group, categoryValue, CraftItemStack.asNMSCopy(result), NonNullList.of(null, ingredientList.toArray(new Ingredient[0])));
        RecipeHolder<ShapelessRecipe> holder = new RecipeHolder<>(key, recipe);
        ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager().addRecipe(holder);
    }

    @Override
    public String getJsonString(ItemStack itemStack) {
        String json = CraftItemStack.asNMSCopy(itemStack).getDisplayName().getStyle().toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return json.substring(176, json.length() - 185);
    }

    @Override
    public String getRawHoverText(ItemStack itemStack) {
        net.minecraft.nbt.CompoundTag tag = CraftItemStack.asNMSCopy(itemStack).getTag();
        if (tag == null) {
            return null;
        }
        return tag.toString();
    }

    @Override
    public PlayerProfile getSkullSkin(ItemStack is) {
        net.minecraft.world.item.ItemStack itemStack = CraftItemStack.asNMSCopy(is);
        if (itemStack.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = itemStack.getTag();
            if (tag.contains("SkullOwner", 10)) {
                GameProfile profile = NbtUtils.readGameProfile(tag.getCompound("SkullOwner"));
                if (profile != null) {
                    Property property = Iterables.getFirst(profile.getProperties().get("textures"), null);
                    return new PlayerProfile(profile.getName(), profile.getId(),
                            property != null ? property.value() : null,
                            property != null ? property.signature() : null);
                }
            }
        }
        return null;
    }

    @Override
    public ItemStack setSkullSkin(ItemStack itemStack, PlayerProfile playerProfile) {
        GameProfile gameProfile = new GameProfile(playerProfile.getUniqueId(), playerProfile.getName());
        if (playerProfile.hasTexture()) {
            gameProfile.getProperties().get("textures").clear();
            if (playerProfile.getTextureSignature() != null) {
                gameProfile.getProperties().put("textures", new Property("textures", playerProfile.getTexture(), playerProfile.getTextureSignature()));
            }
            else {
                gameProfile.getProperties().put("textures", new Property("textures", playerProfile.getTexture()));
            }
        }
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        net.minecraft.nbt.CompoundTag tag = nmsItemStack.hasTag() ? nmsItemStack.getTag() : new net.minecraft.nbt.CompoundTag();
        tag.put("SkullOwner", NbtUtils.writeGameProfile(new net.minecraft.nbt.CompoundTag(), gameProfile));
        nmsItemStack.setTag(tag);
        return CraftItemStack.asBukkitCopy(nmsItemStack);
    }

    @Override
    public ItemStack addNbtData(ItemStack itemStack, String key, Tag value) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        net.minecraft.nbt.CompoundTag tag = nmsItemStack.hasTag() ? nmsItemStack.getTag() : new net.minecraft.nbt.CompoundTag();
        CompoundTag compound = CompoundTagImpl.fromNMSTag(tag).createBuilder().put(key, value).build();
        nmsItemStack.setTag(((CompoundTagImpl) compound).toNMSTag());
        return CraftItemStack.asBukkitCopy(nmsItemStack);
    }

    @Override
    public CompoundTag getNbtData(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        if (nmsItemStack != null && nmsItemStack.hasTag()) {
            return CompoundTagImpl.fromNMSTag(nmsItemStack.getTag());
        }
        return new CompoundTagImpl(new HashMap<>());
    }

    @Override
    public ItemStack setNbtData(ItemStack itemStack, CompoundTag compoundTag) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        nmsItemStack.setTag(((CompoundTagImpl) compoundTag).toNMSTag());
        return CraftItemStack.asBukkitCopy(nmsItemStack);
    }

    @Override
    public void setInventoryItem(Inventory inventory, ItemStack item, int slot) {
        if (inventory instanceof CraftInventoryPlayer && ((CraftInventoryPlayer) inventory).getInventory().player == null) {
            ((CraftInventoryPlayer) inventory).getInventory().setItem(slot, CraftItemStack.asNMSCopy(item));
        }
        else {
            inventory.setItem(slot, item);
        }
    }

    @Override
    public IntArrayTag convertUuidToNbt(UUID id) {
        return new IntArrayTag(NbtUtils.createUUID(id).getAsIntArray());
    }

    @Override
    public UUID convertNbtToUuid(IntArrayTag id) {
        return NbtUtils.loadUUID(new net.minecraft.nbt.IntArrayTag(id.getValue()));
    }

    @Override
    public String getDisplayName(ItemTag item) {
        if (!item.getItemMeta().hasDisplayName()) {
            return null;
        }
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(item.getItemStack());
        String jsonText = ((net.minecraft.nbt.CompoundTag) nmsItemStack.getTag().get("display")).getString("Name");
        try {
            BaseComponent[] nameComponent = ComponentSerializer.parse(jsonText);
            return FormattedTextHelper.stringify(nameComponent);
        }
        catch (Throwable ex) {
            Debug.echoError(ex);
            return null;
        }
    }

    @Override
    public List<String> getLore(ItemTag item) {
        if (!item.getItemMeta().hasLore()) {
            return null;
        }
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(item.getItemStack());
        ListTag list = ((net.minecraft.nbt.CompoundTag) nmsItemStack.getTag().get("display")).getList("Lore", 8);
        List<String> outList = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                BaseComponent[] lineComponent = ComponentSerializer.parse(list.getString(i));
                outList.add(FormattedTextHelper.stringify(lineComponent));
            }
            catch (Throwable ex) {
                Debug.echoError(ex);
            }
        }
        return outList;
    }

    @Override
    public void setDisplayName(ItemTag item, String name) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(item.getItemStack());
        net.minecraft.nbt.CompoundTag tag = nmsItemStack.getOrCreateTag();
        net.minecraft.nbt.CompoundTag display = tag.getCompound("display");
        if (!tag.contains("display")) {
            tag.put("display", display);
        }
        if (name == null || name.isEmpty()) {
            display.put("Name", null);
            return;
        }
        BaseComponent[] components = FormattedTextHelper.parse(name, ChatColor.WHITE);
        display.put("Name", net.minecraft.nbt.StringTag.valueOf(FormattedTextHelper.componentToJson(components)));
        item.setItemStack(CraftItemStack.asBukkitCopy(nmsItemStack));
    }

    @Override
    public void setLore(ItemTag item, List<String> lore) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(item.getItemStack());
        net.minecraft.nbt.CompoundTag tag = nmsItemStack.getOrCreateTag();
        net.minecraft.nbt.CompoundTag display = tag.getCompound("display");
        if (!tag.contains("display")) {
            tag.put("display", display);
        }
        if (lore == null || lore.isEmpty()) {
            display.remove("Lore");
            if (display.isEmpty()) {
                tag.remove("display");
            }
        }
        else {
            ListTag tagList = new ListTag();
            for (String line : lore) {
                tagList.add(net.minecraft.nbt.StringTag.valueOf(FormattedTextHelper.componentToJson(FormattedTextHelper.parse(line, ChatColor.WHITE))));
            }
            display.put("Lore", tagList);
        }
        item.setItemStack(CraftItemStack.asBukkitCopy(nmsItemStack));
    }

    /**
     * Copied from MapItem.getCorrectStateForFluidBlock.
     */
    public static BlockState getCorrectStateForFluidBlock(Level world, BlockState blockState, BlockPos blockPos) {
        FluidState fluid = blockState.getFluidState();
        return !fluid.isEmpty() && !blockState.isFaceSturdy(world, blockPos, Direction.UP) ? fluid.createLegacyBlock() : blockState;
    }

    /**
     * Copied from MapItem.update, redesigned slightly to render totally rather than just relative to a player.
     * Some variables manually renamed for readability.
     */
    public static void renderFullMap(MapItemSavedData worldmap, int xMin, int zMin, int xMax, int zMax) {
        Level world = ((CraftWorld) worldmap.mapView.getWorld()).getHandle();
        int scale = 1 << worldmap.scale;
        int mapX = worldmap.centerX;
        int mapZ = worldmap.centerZ;
        for (int x = xMin; x < xMax; x++) {
            double d0 = 0.0D;
            for (int z = zMin; z < zMax; z++) {
                int k2 = (mapX / scale + x - 64) * scale;
                int l2 = (mapZ / scale + z - 64) * scale;
                Multiset<MapColor> multiset = LinkedHashMultiset.create();
                LevelChunk chunk = world.getChunkAt(new BlockPos(k2, 0, l2));
                if (!chunk.isEmpty()) {
                    ChunkPos chunkcoordintpair = chunk.getPos();
                    int i3 = k2 & 15;
                    int j3 = l2 & 15;
                    int k3 = 0;
                    double d1 = 0.0D;
                    if (world.dimensionType().hasCeiling()) {
                        int l3 = k2 + l2 * 231871;
                        l3 = l3 * l3 * 31287121 + l3 * 11;
                        if ((l3 >> 20 & 1) == 0) {
                            multiset.add(Blocks.DIRT.defaultBlockState().getMapColor(world, BlockPos.ZERO), 10);
                        }
                        else {
                            multiset.add(Blocks.STONE.defaultBlockState().getMapColor(world, BlockPos.ZERO), 100);
                        }

                        d1 = 100.0D;
                    }
                    else {
                        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
                        BlockPos.MutableBlockPos blockposition_mutableblockposition1 = new BlockPos.MutableBlockPos();
                        for (int i4 = 0; i4 < scale; ++i4) {
                            for (int j4 = 0; j4 < scale; ++j4) {
                                int k4 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i4 + i3, j4 + j3) + 1;
                                BlockState iblockdata;
                                if (k4 <= world.getMinBuildHeight() + 1) {
                                    iblockdata = Blocks.BEDROCK.defaultBlockState();
                                }
                                else {
                                    do {
                                        --k4;
                                        blockposition_mutableblockposition.set(chunkcoordintpair.getMinBlockX() + i4 + i3, k4, chunkcoordintpair.getMinBlockZ() + j4 + j3);
                                        iblockdata = chunk.getBlockState(blockposition_mutableblockposition);
                                    } while (iblockdata.getMapColor(world, blockposition_mutableblockposition) == MapColor.NONE && k4 > world.getMinBuildHeight());
                                    if (k4 > world.getMinBuildHeight() && !iblockdata.getFluidState().isEmpty()) {
                                        int l4 = k4 - 1;
                                        blockposition_mutableblockposition1.set(blockposition_mutableblockposition);

                                        BlockState iblockdata1;
                                        do {
                                            blockposition_mutableblockposition1.setY(l4--);
                                            iblockdata1 = chunk.getBlockState(blockposition_mutableblockposition1);
                                            k3++;
                                        } while (l4 > world.getMinBuildHeight() && !iblockdata1.getFluidState().isEmpty());
                                        iblockdata = getCorrectStateForFluidBlock(world, iblockdata, blockposition_mutableblockposition);
                                    }
                                }
                                worldmap.checkBanners(world, chunkcoordintpair.getMinBlockX() + i4 + i3, chunkcoordintpair.getMinBlockZ() + j4 + j3);
                                d1 += (double) k4 / (double) (scale * scale);
                                multiset.add(iblockdata.getMapColor(world, blockposition_mutableblockposition));
                            }
                        }
                    }
                    k3 /= scale * scale;
                    double d2 = (d1 - d0) * 4.0D / (double) (scale + 4) + ((double) (x + z & 1) - 0.5D) * 0.4D;
                    byte b0 = 1;
                    if (d2 > 0.6D) {
                        b0 = 2;
                    }
                    if (d2 < -0.6D) {
                        b0 = 0;
                    }
                    MapColor materialmapcolor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
                    if (materialmapcolor == MapColor.WATER) {
                        d2 = (double) k3 * 0.1D + (double) (x + z & 1) * 0.2D;
                        b0 = 1;
                        if (d2 < 0.5D) {
                            b0 = 2;
                        }
                        if (d2 > 0.9D) {
                            b0 = 0;
                        }
                    }
                    d0 = d1;
                    worldmap.updateColor(x, z, (byte) (materialmapcolor.id * 4 + b0));
                }
            }
        }
    }

    @Override
    public boolean renderEntireMap(int mapId, int xMin, int zMin, int xMax, int zMax) {
        MapItemSavedData worldmap = ((CraftServer) Bukkit.getServer()).getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD).getMapData("map_" + mapId);
        if (worldmap == null) {
            return false;
        }
        renderFullMap(worldmap, xMin, zMin, xMax, zMax);
        return true;
    }

    @Override
    public BlockData getPlacedBlock(Material material) {
        Item nmsItem = BuiltInRegistries.ITEM.getOptional(CraftNamespacedKey.toMinecraft(material.getKey())).orElse(null);
        if (nmsItem instanceof BlockItem) {
            Block block = ((BlockItem) nmsItem).getBlock();
            return CraftBlockData.fromData(block.defaultBlockState());
        }
        return null;
    }

    @Override
    public boolean isValidMix(ItemStack input, ItemStack ingredient) {
        net.minecraft.world.item.ItemStack nmsInput = CraftItemStack.asNMSCopy(input);
        net.minecraft.world.item.ItemStack nmsIngredient = CraftItemStack.asNMSCopy(ingredient);
        return PotionBrewing.hasMix(nmsInput, nmsIngredient);
    }

    public static Class<?> PaperPotionMix_CLASS = null;
    public static Map<NamespacedKey, BrewingRecipe> customBrewingRecipes = null;

    @Override
    public Map<NamespacedKey, BrewingRecipe> getCustomBrewingRecipes() {
        if (customBrewingRecipes == null) {
            customBrewingRecipes = Maps.transformValues((Map<NamespacedKey, ?>) ReflectionHelper.getFieldValue(PotionBrewing.class, "CUSTOM_MIXES", null), paperMix -> {
                if (PaperPotionMix_CLASS == null) {
                    PaperPotionMix_CLASS = paperMix.getClass();
                }
                RecipeChoice ingredient = CraftRecipe.toBukkit(ReflectionHelper.getFieldValue(PaperPotionMix_CLASS, "ingredient", paperMix));
                RecipeChoice input = CraftRecipe.toBukkit(ReflectionHelper.getFieldValue(PaperPotionMix_CLASS, "input", paperMix));
                ItemStack result = CraftItemStack.asBukkitCopy(ReflectionHelper.getFieldValue(PaperPotionMix_CLASS, "result", paperMix));
                return new BrewingRecipe(ingredient, input, result);
            });
        }
        return customBrewingRecipes;
    }
}
