/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.montoyo.mcef.api.IBrowser;
import net.montoyo.mcef.api.IDisplayHandler;
import net.montoyo.mcef.api.MCEFApi;
import net.montoyo.wd.SharedProxy;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.gui.GuiMinePad;
import net.montoyo.wd.client.gui.GuiScreenConfig;
import net.montoyo.wd.client.gui.GuiSetURL2;
import net.montoyo.wd.client.gui.WDScreen;
import net.montoyo.wd.client.gui.loading.GuiLoader;
import net.montoyo.wd.client.renderers.IModelBaker;
import net.montoyo.wd.client.renderers.MinePadRenderer;
import net.montoyo.wd.client.renderers.ScreenBaker;
import net.montoyo.wd.client.renderers.ScreenRenderer;
import net.montoyo.wd.data.GuiData;
import net.montoyo.wd.entity.TileEntityScreen;
import net.montoyo.wd.net.SMessagePadCtrl;
import net.montoyo.wd.utilities.*;

import java.util.ArrayList;
import java.util.HashMap;

public class ClientProxy extends SharedProxy implements IResourceManagerReloadListener, IDisplayHandler {

    public class PadData {

        public IBrowser view;
        private boolean isInHotbar;
        private int id;
        private long lastURLSent;

        private PadData(String url, int id) {
            view = mcef.createBrowser(url);
            view.resize((int) WebDisplays.INSTANCE.padResX, (int) WebDisplays.INSTANCE.padResY);
            isInHotbar = true;
            this.id = id;
        }

    }

    private Minecraft mc;
    private ArrayList<ResourceModelPair> modelBakers = new ArrayList<>();
    private net.montoyo.mcef.api.API mcef;
    private MinePadRenderer minePadRenderer;

    //Tracking
    private ArrayList<TileEntityScreen> screenTracking = new ArrayList<>();
    private double unloadDistance2 = 32.0 * 32.0;
    private double loadDistance2 = 30.0 * 30.0;
    private int lastTracked = 0;

    //MinePads Management
    private HashMap<Integer, PadData> padMap = new HashMap<>();
    private ArrayList<PadData> padList = new ArrayList<>();
    private int minePadTickCounter = 0;

    /**************************************** INHERITED METHODS ****************************************/

    @Override
    public void preInit() {
        mc = Minecraft.getMinecraft();
        MinecraftForge.EVENT_BUS.register(this);
        registerCustomBlockBaker(new ScreenBaker(), WebDisplays.INSTANCE.blockScreen);
    }

    @Override
    public void init() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityScreen.class, new ScreenRenderer());
        mcef = MCEFApi.getAPI();
        minePadRenderer = new MinePadRenderer();
    }

    @Override
    public void postInit() {
        ((SimpleReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);

        if(mcef == null)
            throw new RuntimeException("MCEF is missing");

        mcef.registerDisplayHandler(this);
    }

    @Override
    public World getWorld(int dim) {
        World ret = mc.world;
        if(dim == CURRENT_DIMENSION)
            return ret;

        if(ret.provider.getDimension() != dim)
            throw new RuntimeException("Can't get non-current dimension " + dim + " from client.");

        return ret;
    }

    @Override
    public void enqueue(Runnable r) {
        mc.addScheduledTask(r);
    }

    @Override
    public void displayGui(GuiData data) {
        GuiScreen gui = data.createGui(mc.currentScreen, mc.world);
        if(gui != null)
            mc.displayGuiScreen(gui);
    }

    @Override
    public void trackScreen(TileEntityScreen tes, boolean track) {
        int idx = -1;
        for(int i = 0; i < screenTracking.size(); i++) {
            if(screenTracking.get(i) == tes) {
                idx = i;
                break;
            }
        }

        if(track) {
            if(idx < 0)
                screenTracking.add(tes);
        } else
            screenTracking.remove(idx);
    }

    @Override
    public void onAutocompleteResult(NameUUIDPair[] pairs) {
        if(mc.currentScreen != null && mc.currentScreen instanceof WDScreen) {
            if(pairs.length == 0)
                ((WDScreen) mc.currentScreen).onAutocompleteFailure();
            else
                ((WDScreen) mc.currentScreen).onAutocompleteResult(pairs);
        }
    }

    @Override
    public GameProfile[] getOnlineGameProfiles() {
        return new GameProfile[] { mc.player.getGameProfile() };
    }

    @Override
    public void screenUpdateResolutionInGui(Vector3i pos, BlockSide side, Vector2i res) {
        if(mc.currentScreen != null && mc.currentScreen instanceof GuiScreenConfig) {
            GuiScreenConfig gsc = (GuiScreenConfig) mc.currentScreen;

            if(gsc.isScreen(pos, side))
                gsc.updateResolution(res);
        }
    }

    @Override
    public void displaySetPadURLGui(String padURL) {
        mc.displayGuiScreen(new GuiSetURL2(padURL));
    }

    @Override
    public void openMinePadGui(int padId) {
        PadData pd = padMap.get(padId);

        if(pd != null && pd.view != null)
            mc.displayGuiScreen(new GuiMinePad(pd));
    }

    /**************************************** RESOURCE MANAGER METHODS ****************************************/

    @Override
    public void onResourceManagerReload(IResourceManager rm) {
        Log.info("Resource manager reload: clearing GUI cache...");
        GuiLoader.clearCache();
    }

    /**************************************** DISPLAY HANDLER METHODS ****************************************/

    @Override
    public void onAddressChange(IBrowser browser, String url) {
        if(browser != null) {
            long t = System.currentTimeMillis();

            for(PadData pd : padList) {
                if(pd.view == browser && t - pd.lastURLSent >= 1000) {
                    pd.lastURLSent = t; //Avoid spamming the server with porn URLs
                    WebDisplays.NET_HANDLER.sendToServer(new SMessagePadCtrl(pd.id, url));
                    break;
                }
            }
        }
    }

    @Override
    public void onTitleChange(IBrowser browser, String title) {
    }

    @Override
    public void onTooltip(IBrowser browser, String text) {
    }

    @Override
    public void onStatusMessage(IBrowser browser, String value) {
    }

    /**************************************** EVENT METHODS ****************************************/

    @SubscribeEvent
    public void onStitchTextures(TextureStitchEvent.Pre ev) {
        TextureMap texMap = ev.getMap();

        if(texMap == mc.getTextureMapBlocks()) {
            for(ResourceModelPair pair : modelBakers)
                pair.getModel().loadTextures(texMap);
        }
    }

    @SubscribeEvent
    public void onBakeModel(ModelBakeEvent ev) {
        for(ResourceModelPair pair : modelBakers)
            ev.getModelRegistry().putObject(pair.getResourceLocation(), pair.getModel());
    }

    @SubscribeEvent
    public void onRegisterModels(ModelRegistryEvent ev) {
        final WebDisplays wd = WebDisplays.INSTANCE;

        registerItemModel(wd.blockScreen.getItem(), 0, "inventory");
        ModelLoader.setCustomModelResourceLocation(wd.blockPeripheral.getItem(), 0, new ModelResourceLocation("webdisplays:kb_inv", "normal"));
        registerItemModel(wd.blockPeripheral.getItem(), 1, "facing=0,type=remotectrl");
        registerItemModel(wd.blockPeripheral.getItem(), 2, "facing=0,type=ccinterface"); //TODO: This doesn't work...
        registerItemModel(wd.blockPeripheral.getItem(), 3, "facing=0,type=cointerface");
        registerItemModel(wd.itemScreenCfg, 0, "normal");
        registerItemModel(wd.itemOwnerThief, 0, "normal");
        registerItemModel(wd.itemLinker, 0, "normal");
        registerItemModel(wd.itemStoneKey, 0, "normal");
        registerItemModel(wd.itemMinePad, 0, "normal");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent ev) {
        if(ev.phase == TickEvent.Phase.END) {
            //Unload/load screens depending on client player distance
            if(mc.player != null && !screenTracking.isEmpty()) {
                int id = lastTracked % screenTracking.size();
                lastTracked++;

                TileEntityScreen tes = screenTracking.get(id);
                double dist2 = mc.player.getDistanceSq(tes.getPos());

                if(tes.isLoaded()) {
                    if(dist2 > unloadDistance2)
                        tes.unload();
                    else
                        tes.updateTrackDistance(dist2);
                } else if(dist2 <= loadDistance2)
                    tes.load();
            }

            //Load/unload minePads depending on which item is in the player's hand
            if(++minePadTickCounter >= 10) {
                minePadTickCounter = 0;
                EntityPlayer ep = mc.player;

                for(PadData pd: padList)
                    pd.isInHotbar = false;

                if(ep != null) {
                    updateInventory(ep.inventory.mainInventory, ep.getHeldItem(EnumHand.MAIN_HAND), 9);
                    updateInventory(ep.inventory.offHandInventory, ep.getHeldItem(EnumHand.OFF_HAND), 1); //Is this okay?
                }

                //TODO: Check for GuiContainer.draggedStack

                for(int i = padList.size() - 1; i >= 0; i--) {
                    PadData pd = padList.get(i);

                    if(!pd.isInHotbar) {
                        pd.view.close();
                        pd.view = null; //This is for GuiMinePad, in case the player dies with the GUI open
                        padList.remove(i);
                        padMap.remove(pd.id);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderPlayerHand(RenderSpecificHandEvent ev) {
        if(ev.getItemStack().getItem() == WebDisplays.INSTANCE.itemMinePad) {
            EnumHandSide handSide = mc.player.getPrimaryHand();
            if(ev.getHand() == EnumHand.OFF_HAND)
                handSide = handSide.opposite();

            minePadRenderer.render(ev.getItemStack(), (handSide == EnumHandSide.RIGHT) ? 1.0f : -1.0f, ev.getSwingProgress(), ev.getEquipProgress());
            ev.setCanceled(true);
        }
    }

    /**************************************** OTHER METHODS ****************************************/

    private void updateInventory(NonNullList<ItemStack> inv, ItemStack heldStack, int cnt) {
        for(int i = 0; i < cnt; i++) {
            ItemStack item = inv.get(i);

            if(item.getItem() == WebDisplays.INSTANCE.itemMinePad) {
                NBTTagCompound tag = item.getTagCompound();

                if(tag != null && tag.hasKey("PadID"))
                    updatePad(tag.getInteger("PadID"), tag, item == heldStack);
            }
        }
    }

    private void registerCustomBlockBaker(IModelBaker baker, Block block0) {
        ModelResourceLocation normalLoc = new ModelResourceLocation(block0.getRegistryName(), "normal");
        ResourceModelPair pair = new ResourceModelPair(normalLoc, baker);
        modelBakers.add(pair);
        ModelLoader.setCustomStateMapper(block0, new StaticStateMapper(normalLoc));
    }

    private void registerItemModel(Item item, int meta, String variant) {
        ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(item.getRegistryName(), variant));
    }

    private void updatePad(int id, NBTTagCompound tag, boolean isSelected) {
        PadData pd = padMap.get(id);

        if(pd != null)
            pd.isInHotbar = true;
        else if(isSelected && tag.hasKey("PadURL")) {
            pd = new PadData(tag.getString("PadURL"), id);
            padMap.put(id, pd);
            padList.add(pd);
        }
    }

    public MinePadRenderer getMinePadRenderer() {
        return minePadRenderer;
    }

    public PadData getPadByID(int id) {
        return padMap.get(id);
    }

    public net.montoyo.mcef.api.API getMCEF() {
        return mcef;
    }

}