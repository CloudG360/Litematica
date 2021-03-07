package fi.dy.masa.litematica;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import fi.dy.masa.litematica.event.RenderHandler;
import fi.dy.masa.malilib.event.InitializationHandler;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.MOD_VERSION,
    guiFactory = "fi.dy.masa.litematica.config.gui.LitematicaGuiFactory",
    updateJSON = "https://raw.githubusercontent.com/maruohon/litematica/forge_1.12.2/update.json",
    clientSideOnly=true, acceptedMinecraftVersions = "1.12.2", dependencies = "required-after:malilib;")
public class Litematica
{
    @Mod.Instance(Reference.MOD_ID)
    public static Litematica instance;

    public static final Logger logger = LogManager.getLogger(Reference.MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
        MinecraftForge.EVENT_BUS.register(RenderHandler.INSTANCE);
    }

    @Mod.EventHandler
    public void onFingerPrintViolation(FMLFingerprintViolationEvent event)
    {
        // Why was there ever tamper protection here in the first place?
    }
}
