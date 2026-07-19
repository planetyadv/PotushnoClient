package pothushnoclient.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class POTHUSHNOCLIENTClient implements ClientModInitializer {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pothushnoclient", "main"));

    public static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pothushnoclient.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (ClickGuiScreen.isOpen()) client.setScreenAndShow(null);
                else                         client.setScreenAndShow(new ClickGuiScreen());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  Опция-слайдер
    // ══════════════════════════════════════════════════════════════
    public static class Option {
        private final String name;
        private float        value;
        private final float  min, max;

        public Option(String name, float value, float min, float max) {
            this.name  = name;
            this.value = value;
            this.min   = min;
            this.max   = max;
        }

        public String getName()  { return name; }
        public float  getValue() { return value; }
        public float  getMin()   { return min; }
        public float  getMax()   { return max; }

        public void setValue(float v) {
            this.value = Math.max(min, Math.min(max, v));
        }

        /** Форматирует значение: целое если шаг ≥ 1, иначе одна дробь. */
        public String getFormatted() {
            return (max - min >= 10) ? String.valueOf((int) value)
                                     : String.format("%.1f", value);
        }
    }

    public static class FullbrightModule extends Module {
        private float oldGamma = 1.0f;

        public FullbrightModule(String name, Category category) {
            super(name, category);
        }

        @Override
        protected void onEnable() {
            oldGamma = getGamma();
            applyGamma();
        }

        @Override
        protected void onDisable() {
            setGamma(oldGamma);
        }

        @Override
        public void applyOptions() {
            applyGamma();
        }

        private float getGamma() {
            try {
                return Minecraft.getInstance().options.gamma().get().floatValue();
            } catch (Exception e) {
                return 1.0f;
            }
        }

        private void setGamma(float value) {
            try {
                // Получаем сам объект опции гаммы
                Object gammaOption = Minecraft.getInstance().options.gamma();
                
                // Пробуем обойти лимиты через прямое изменение приватного поля 'value' внутри OptionInstance/SimpleOption
                java.lang.reflect.Field valueField = gammaOption.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                valueField.set(gammaOption, (double) value);
            } catch (Exception e) {
                // Если поле называется иначе в старых/интернированных маппингах (например, 'c' или 'value' в родительском классе)
                try {
                    Object gammaOption = Minecraft.getInstance().options.gamma();
                    java.lang.reflect.Field valueField = gammaOption.getClass().getSuperclass().getDeclaredField("value");
                    valueField.setAccessible(true);
                    valueField.set(gammaOption, (double) value);
                } catch (Exception ex) {
                    // Грязный резервный вариант: если гамма задушена, даём игроку эффект Ночного Зрения (клиентский)
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        if (value > 1.0f) {
                            mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.NIGHT_VISION, 999999, 0, false, false
                            ));
                        } else {
                            mc.player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                        }
                    }
                }
            }
        }

        private void applyGamma() {
            if (isEnabled()) {
                Option opt = getOptions().stream()
                        .filter(o -> o.getName().equals("Gamma"))
                        .findFirst()
                        .orElse(null);
                if (opt != null) {
                    setGamma(opt.getValue());
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Модуль
    // ══════════════════════════════════════════════════════════════
    public static class Module {
        private final String       name;
        private final Category     category;
        private boolean            enabled;
        private boolean            expanded;   // раскрыты ли опции
        private final List<Option> options = new ArrayList<>();

        public Module(String name, Category category) {
            this.name     = name;
            this.category = category;
        }

        // 👇 ДОБАВЛЯЕМ ЭТОТ МЕТОД
        public void applyOptions() {
            // пустая реализация
        }

        public Module addOption(Option o) { options.add(o); return this; }

        public String       getName()     { return name; }
        public Category     getCategory() { return category; }
        public boolean      isEnabled()   { return enabled; }
        public boolean      isExpanded()  { return expanded; }
        public List<Option> getOptions()  { return options; }
        public boolean      hasOptions()  { return !options.isEmpty(); }

        public void toggle()           { enabled = !enabled; if (enabled) onEnable(); else onDisable(); }
        public void toggleExpand()     { expanded = !expanded; }

        protected void onEnable()  { System.out.println(name + " включён"); }
        protected void onDisable() { System.out.println(name + " выключен"); }

        public enum Category {
            COMBAT("Combat"), MOVEMENT("Movement"), RENDER("Render"), MISC("Misc");
            private final String dn;
            Category(String d) { this.dn = d; }
            public String getDisplayName() { return dn; }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Список модулей с опциями
    // ══════════════════════════════════════════════════════════════
    static final Map<Module.Category, List<Module>> MODULES = new EnumMap<>(Module.Category.class);

    static {
        for (Module.Category c : Module.Category.values()) MODULES.put(c, new ArrayList<>());

        MODULES.get(Module.Category.COMBAT).add(
                new Module("Killaura", Module.Category.COMBAT)
                        .addOption(new Option("Range",  3.0f, 2.0f, 6.0f))
                        .addOption(new Option("APS",   10.0f, 1.0f, 20.0f)));

        MODULES.get(Module.Category.COMBAT).add(
                new Module("AutoClicker", Module.Category.COMBAT)
                        .addOption(new Option("CPS", 8.0f, 1.0f, 20.0f)));

        MODULES.get(Module.Category.COMBAT).add(
                new Module("Reach", Module.Category.COMBAT)
                        .addOption(new Option("Range", 3.5f, 3.0f, 6.0f)));

        MODULES.get(Module.Category.MOVEMENT).add(
                new Module("Speed", Module.Category.MOVEMENT)
                        .addOption(new Option("Multiplier", 1.5f, 1.0f, 5.0f)));

        MODULES.get(Module.Category.MOVEMENT).add(
                new Module("Flight", Module.Category.MOVEMENT)
                        .addOption(new Option("Speed", 1.0f, 0.1f, 3.0f)));

        MODULES.get(Module.Category.MOVEMENT).add(
                new Module("NoFall", Module.Category.MOVEMENT));   // без опций

        MODULES.get(Module.Category.RENDER).add(
                new Module("ESP", Module.Category.RENDER)
                        .addOption(new Option("Distance", 50.0f, 10.0f, 100.0f)));

        MODULES.get(Module.Category.RENDER).add(
            new FullbrightModule("Fullbright", Module.Category.RENDER)
                .addOption(new Option("Gamma", 10.0f, 1.0f, 20.0f))
);

        MODULES.get(Module.Category.RENDER).add(
                new Module("Tracers", Module.Category.RENDER));    // без опций

        MODULES.get(Module.Category.MISC).add(
                new Module("AutoText",      Module.Category.MISC));
        MODULES.get(Module.Category.MISC).add(
                new Module("Notifications", Module.Category.MISC)
                        .addOption(new Option("Duration", 3.0f, 1.0f, 10.0f)));
    }

    // ══════════════════════════════════════════════════════════════
    //  ClickGUI Screen
    // ══════════════════════════════════════════════════════════════
    public static class ClickGuiScreen extends Screen {

        // ── Геометрия ───────────────────────────────────────────────
        private static final int PANEL_W   = 340;
        private static final int PANEL_H   = 240;
        private static final int TITLE_H   = 22;
        private static final int TAB_W     = 76;
        private static final int ROW_H     = 18;
        private static final int OPT_H     = 30;   // высота строки опции
        private static final int CLOSE_SZ  = 14;
        private static final int SUPPORT_H = 22;
        private static final int EXPAND_W  = 16;   // зона кнопки ▸/▾

        // ── Ссылка ─────────────────────────────────────────────────
        private static final String SUPPORT_URL =
                "https://tviykrok.com.ua/?gad_source=1&gad_campaignid=21451356647" +
                "&gclid=Cj0KCQjw6_HSBhCpARIsANvVlta77xuDU5db09l0-eBx1b8srOfsark" +
                "LwR1pugt4fiGf6-kz2S7xPHEaAnRcEALw_wcB";

        // ── Палитра ─────────────────────────────────────────────────
        private static final int BLUE_BRIGHT  = 0xFF1565C0;
        private static final int BLUE_MID     = 0xFF0D47A1;
        private static final int BLUE_DARK    = 0xFF082070;
        private static final int BLUE_DEEPER  = 0xFF04144A;
        private static final int YELLOW_HOT   = 0xFFFFD600;
        private static final int YELLOW_WARM  = 0xFFFFAB00;
        private static final int YELLOW_DIM   = 0xFFB8860B;
        private static final int BG_TOP       = 0xF2020816;
        private static final int BG_BOT       = 0xF2050F25;
        private static final int ROW_ON_TOP   = 0xE0102B50;
        private static final int ROW_ON_BOT   = 0xE0071A38;
        private static final int ROW_OFF_TOP  = 0xE0090D1C;
        private static final int ROW_OFF_BOT  = 0xE0050B14;
        private static final int ROW_HOV_TOP  = 0xE0163263;
        private static final int ROW_HOV_BOT  = 0xE00C1D40;
        private static final int OPT_BG_TOP   = 0xF0030614;
        private static final int OPT_BG_BOT   = 0xF001040E;
        private static final int DIVIDER      = 0xFF0A2050;
        private static final int TEXT_MUTED   = 0xFF7090C0;
        private static final int TEXT_WHITE   = 0xFFFFFFFF;
        private static final int SUP_TOP      = 0xEE002080;
        private static final int SUP_BOT      = 0xEE001050;
        private static final int SUP_HOV_TOP  = 0xEE0040B8;
        private static final int SUP_HOV_BOT  = 0xEE002878;

        // ── Состояние ───────────────────────────────────────────────
        private static boolean open     = false;
        private int     panelX, panelY;
        private boolean dragging        = false;
        private double  dragOffX, dragOffY;
        private Module.Category selectedTab = Module.Category.COMBAT;

        // ── Состояние перетаскивания слайдера ────────────────────────
        private Option draggingSlider = null;
        private Module draggingModule = null;
        private int    sliderBarX;          // абсолютная X левой стороны полосы
        private int    sliderBarW;          // ширина полосы

        public ClickGuiScreen() { super(Component.literal("PotushnoClient")); }

        public static boolean isOpen() { return open; }

        @Override
        protected void init() {
            open   = true;
            panelX = (this.width  - PANEL_W) / 2;
            panelY = (this.height - PANEL_H) / 2;
        }

        @Override
        public void onClose() { open = false; super.onClose(); }

        // ════════════════════════════════════════════════════════════
        //  РЕНДЕР
        // ════════════════════════════════════════════════════════════
        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            final int px = panelX, py = panelY;
            final int pw = PANEL_W, ph = PANEL_H;

            // Тело панели
            g.fillGradient(px, py, px + pw, py + ph, BG_TOP, BG_BOT);

            // Жёлтая рамка
            g.fillGradient(px,          py,          px + pw,     py + 1,      YELLOW_HOT,  YELLOW_WARM);
            g.fillGradient(px,          py + ph - 1, px + pw,     py + ph,     YELLOW_WARM, YELLOW_HOT);
            g.fillGradient(px,          py,          px + 1,      py + ph,     YELLOW_HOT,  YELLOW_WARM);
            g.fillGradient(px + pw - 1, py,          px + pw,     py + ph,     YELLOW_WARM, YELLOW_HOT);

            // Шапка
            g.fillGradient(px, py, px + pw, py + TITLE_H, BLUE_BRIGHT, BLUE_DARK);
            g.fillGradient(px, py + TITLE_H, px + pw / 2, py + TITLE_H + 2, YELLOW_HOT,  YELLOW_WARM);
            g.fillGradient(px + pw / 2, py + TITLE_H, px + pw, py + TITLE_H + 2, YELLOW_WARM, YELLOW_HOT);
            g.textRenderer().accept(px + 8, py + (TITLE_H - 8) / 2,
                    Component.literal("✦ PotushnoClient").withColor(YELLOW_HOT));

            // Кнопка ✕
            int cx  = px + pw - CLOSE_SZ - 5;
            int cy  = py + (TITLE_H - CLOSE_SZ) / 2;
            boolean hCl = inside(mouseX, mouseY, cx, cy, CLOSE_SZ, CLOSE_SZ);
            g.fillGradient(cx, cy, cx + CLOSE_SZ, cy + CLOSE_SZ,
                    hCl ? 0xFFD32F2F : 0xFF8B0000, hCl ? 0xFF9A0000 : 0xFF5A0000);
            g.fillGradient(cx, cy, cx + CLOSE_SZ, cy + 1, YELLOW_DIM, YELLOW_DIM);
            g.fillGradient(cx, cy + CLOSE_SZ - 1, cx + CLOSE_SZ, cy + CLOSE_SZ, YELLOW_DIM, YELLOW_DIM);
            g.fillGradient(cx, cy, cx + 1, cy + CLOSE_SZ, YELLOW_DIM, YELLOW_DIM);
            g.fillGradient(cx + CLOSE_SZ - 1, cy, cx + CLOSE_SZ, cy + CLOSE_SZ, YELLOW_DIM, YELLOW_DIM);
            g.textRenderer().accept(cx + 3, cy + 3, Component.literal("✕").withColor(TEXT_WHITE));

            // ── Вкладки ─────────────────────────────────────────────
            int tabY = py + TITLE_H + 2;
            int idx  = 0;
            for (Module.Category cat : Module.Category.values()) {
                int     ty  = tabY + idx * ROW_H;
                boolean sel = cat == selectedTab;
                boolean hov = inside(mouseX, mouseY, px, ty, TAB_W, ROW_H);
                int t1 = sel ? BLUE_MID : (hov ? 0xFF0D2D60 : 0xFF060D1C);
                int t2 = sel ? BLUE_DEEPER : (hov ? 0xFF071840 : 0xFF030810);
                g.fillGradient(px, ty, px + TAB_W, ty + ROW_H, t1, t2);
                if (sel) g.fillGradient(px, ty, px + 3, ty + ROW_H, YELLOW_HOT, YELLOW_WARM);
                int tc = sel ? YELLOW_HOT : (hov ? YELLOW_WARM : TEXT_MUTED);
                g.textRenderer().accept(px + 10, ty + (ROW_H - 8) / 2,
                        Component.literal(cat.getDisplayName()).withColor(tc));
                idx++;
            }

            // Вертикальный разделитель
            int divTop = py + TITLE_H + 2;
            int divBot = py + ph - SUPPORT_H - 1;
            g.fillGradient(px + TAB_W, divTop, px + TAB_W + 1, divBot, YELLOW_DIM, DIVIDER);

            // ── Модули ──────────────────────────────────────────────
            List<Module> mods = MODULES.get(selectedTab);
            int modX = px + TAB_W + 1;
            int modW = pw - TAB_W - 2;
            int curY = py + TITLE_H + 2;   // текущая Y-позиция (динамическая)

            for (Module m : mods) {
                // Высота этого модуля с учётом раскрытых опций
                int totalH = ROW_H + (m.isExpanded() ? m.getOptions().size() * OPT_H : 0);

                // Не рендерим за пределами панели
                if (curY + ROW_H > py + ph - SUPPORT_H - 1) break;

                boolean en  = m.isEnabled();
                boolean hov = inside(mouseX, mouseY, modX, curY, modW, ROW_H);

                // ── Строка модуля ─────────────────────────────────
                int r1 = en ? ROW_ON_TOP : (hov ? ROW_HOV_TOP : ROW_OFF_TOP);
                int r2 = en ? ROW_ON_BOT : (hov ? ROW_HOV_BOT : ROW_OFF_BOT);
                g.fillGradient(modX, curY, modX + modW, curY + ROW_H, r1, r2);
                g.fillGradient(modX, curY + ROW_H - 1, modX + modW, curY + ROW_H, DIVIDER, DIVIDER);

                // Индикатор вкл/выкл
                int iSz = 6;
                int iX  = modX + modW - iSz - (m.hasOptions() ? EXPAND_W + 4 : 7);
                int iY  = curY + (ROW_H - iSz) / 2;
                if (en) {
                    g.fillGradient(iX, iY, iX + iSz, iY + iSz, YELLOW_HOT, YELLOW_WARM);
                } else {
                    g.fillGradient(iX, iY, iX + iSz, iY + iSz, 0xFF0D1A33, 0xFF060E1F);
                    g.fillGradient(iX, iY, iX + iSz, iY + 1, YELLOW_DIM, YELLOW_DIM);
                    g.fillGradient(iX, iY + iSz - 1, iX + iSz, iY + iSz, YELLOW_DIM, YELLOW_DIM);
                    g.fillGradient(iX, iY, iX + 1, iY + iSz, YELLOW_DIM, YELLOW_DIM);
                    g.fillGradient(iX + iSz - 1, iY, iX + iSz, iY + iSz, YELLOW_DIM, YELLOW_DIM);
                }

                // Текст модуля
                g.textRenderer().accept(modX + 7, curY + (ROW_H - 8) / 2,
                        Component.literal(m.getName()).withColor(en ? YELLOW_HOT : TEXT_MUTED));

                // Кнопка ▸/▾ (если есть опции)
                if (m.hasOptions()) {
                    int arrowX = modX + modW - EXPAND_W;
                    boolean hAr = inside(mouseX, mouseY, arrowX, curY, EXPAND_W, ROW_H);
                    g.fillGradient(arrowX, curY, arrowX + EXPAND_W, curY + ROW_H,
                            hAr ? 0x400057B7 : 0x20004090,
                            hAr ? 0x400057B7 : 0x20004090);
                    String arrow = m.isExpanded() ? "▾" : "▸";
                    g.textRenderer().accept(arrowX + 4, curY + (ROW_H - 8) / 2,
                            Component.literal(arrow).withColor(m.isExpanded() ? YELLOW_HOT : TEXT_MUTED));
                }

                curY += ROW_H;

                // ── Опции (слайдеры) ─────────────────────────────
                if (m.isExpanded()) {
                    for (Option opt : m.getOptions()) {
                        if (curY + OPT_H > py + ph - SUPPORT_H - 1) break;

                        // Фон опции
                        g.fillGradient(modX, curY, modX + modW, curY + OPT_H, OPT_BG_TOP, OPT_BG_BOT);
                        // Нижняя линия
                        g.fillGradient(modX, curY + OPT_H - 1, modX + modW, curY + OPT_H, DIVIDER, DIVIDER);

                        // Название опции
                        g.textRenderer().accept(modX + 9, curY + 4,
                                Component.literal(opt.getName()).withColor(TEXT_MUTED));

                        // Значение (справа)
                        String valStr = opt.getFormatted();
                        int valX = modX + modW - 8 - valStr.length() * 5;
                        g.textRenderer().accept(valX, curY + 4,
                                Component.literal(valStr).withColor(YELLOW_WARM));

                        // ── Слайдер ──────────────────────────────
                        int barPadLeft  = 9;
                        int barPadRight = 8 + valStr.length() * 5 + 4;
                        int bx = modX + barPadLeft;
                        int bw = modW - barPadLeft - barPadRight;
                        int by = curY + 16;
                        int bh = 4;

                        // Трек: тёмно-синий
                        g.fillGradient(bx, by, bx + bw, by + bh, 0xFF0A1428, 0xFF060E1C);
                        // Жёлтая рамка трека
                        g.fillGradient(bx, by, bx + bw, by + 1, YELLOW_DIM, YELLOW_DIM);
                        g.fillGradient(bx, by + bh - 1, bx + bw, by + bh, YELLOW_DIM, YELLOW_DIM);
                        g.fillGradient(bx, by, bx + 1, by + bh, YELLOW_DIM, YELLOW_DIM);
                        g.fillGradient(bx + bw - 1, by, bx + bw, by + bh, YELLOW_DIM, YELLOW_DIM);

                        // Заполненная часть
                        float t = (opt.getValue() - opt.getMin()) / (opt.getMax() - opt.getMin());
                        int filled = (int)(t * (bw - 2));
                        if (filled > 0) {
                            g.fillGradient(bx + 1, by + 1, bx + 1 + filled, by + bh - 1,
                                    YELLOW_HOT, YELLOW_WARM);
                        }

                        // Ручка слайдера
                        int hndW = 6;
                        int hndH = 10;
                        int hndX = bx + 1 + filled - hndW / 2;
                        int hndY = by - (hndH - bh) / 2;
                        boolean hSlider = inside(mouseX, mouseY, hndX - 2, hndY, hndW + 4, hndH);
                        g.fillGradient(hndX, hndY, hndX + hndW, hndY + hndH,
                                hSlider ? YELLOW_HOT : YELLOW_WARM,
                                hSlider ? YELLOW_WARM : YELLOW_DIM);
                        // Тёмный центр ручки
                        g.fillGradient(hndX + 2, hndY + 2, hndX + hndW - 2, hndY + hndH - 2,
                                0xFF0A1A3A, 0xFF040E20);

                        curY += OPT_H;
                    }
                }
            }

            // Горизонтальный разделитель над Support
            int sepY = py + ph - SUPPORT_H - 1;
            g.fillGradient(px + 1, sepY, px + pw / 2,   sepY + 1, YELLOW_HOT,  YELLOW_WARM);
            g.fillGradient(px + pw / 2, sepY, px + pw - 1, sepY + 1, YELLOW_WARM, YELLOW_HOT);

            // ── Кнопка Support Ukraine ────────────────────────────
            int bx  = px + 1;
            int by2 = py + ph - SUPPORT_H;
            int bw  = pw - 2;
            boolean hSup = inside(mouseX, mouseY, bx, by2, bw, SUPPORT_H);
            g.fillGradient(bx, by2, bx + bw, by2 + SUPPORT_H,
                    hSup ? SUP_HOV_TOP : SUP_TOP, hSup ? SUP_HOV_BOT : SUP_BOT);
            g.fillGradient(bx, by2, bx + bw / 2, by2 + 2, YELLOW_HOT,  YELLOW_WARM);
            g.fillGradient(bx + bw / 2, by2, bx + bw, by2 + 2, YELLOW_WARM, YELLOW_HOT);
            String btnTxt = hSup ? "★ Support Ukraine 💛 ★" : "Support Ukraine 💛";
            int    btnClr = hSup ? YELLOW_HOT : YELLOW_WARM;
            int    tox    = bx + Math.max(6, bw / 2 - btnTxt.length() * 3);
            g.textRenderer().accept(tox, by2 + (SUPPORT_H - 8) / 2,
                    Component.literal(btnTxt).withColor(btnClr));

            super.extractRenderState(g, mouseX, mouseY, delta);
        }

        // ════════════════════════════════════════════════════════════
        //  ВВОД МЫШИ
        // ════════════════════════════════════════════════════════════
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean stillValid) {
            double mx = event.x();
            double my = event.y();
            int btn = event.button();

            if (btn == 0) {
                // Кнопка ✕
                int cx = panelX + PANEL_W - CLOSE_SZ - 5;
                int cy = panelY + (TITLE_H - CLOSE_SZ) / 2;
                if (inside(mx, my, cx, cy, CLOSE_SZ, CLOSE_SZ)) { this.onClose(); return true; }

                // Drag шапки
                if (inside(mx, my, panelX, panelY, PANEL_W, TITLE_H)) {
                    dragging = true; dragOffX = mx - panelX; dragOffY = my - panelY; return true;
                }

                // Support Ukraine
                if (inside(mx, my, panelX + 1, panelY + PANEL_H - SUPPORT_H, PANEL_W - 2, SUPPORT_H)) {
                    openSupportUrl(); return true;
                }

                // Вкладки
                int tabY = panelY + TITLE_H + 2;
                int i = 0;
                for (Module.Category cat : Module.Category.values()) {
                    if (inside(mx, my, panelX, tabY + i * ROW_H, TAB_W, ROW_H)) {
                        selectedTab = cat; return true;
                    }
                    i++;
                }

                // Модули и опции (с динамическим Y)
                List<Module> mods = MODULES.get(selectedTab);
                int modX = panelX + TAB_W + 1;
                int modW = PANEL_W - TAB_W - 2;
                int curY = panelY + TITLE_H + 2;

                for (Module m : mods) {
                    if (curY + ROW_H > panelY + PANEL_H - SUPPORT_H - 1) break;

                    // Кликаем по строке модуля
                    if (inside(mx, my, modX, curY, modW, ROW_H)) {

                        int arrowX = modX + modW - EXPAND_W;

                        if (m.hasOptions() && inside(mx, my, arrowX, curY, EXPAND_W, ROW_H)) {
                            m.toggleExpand();
                        } else {
                            m.toggle();
                        }

                        return true;
                    }
                    curY += ROW_H;

                    // Кликаем по опциям
                    if (m.isExpanded()) {
                        for (Option opt : m.getOptions()) {
                            if (curY + OPT_H > panelY + PANEL_H - SUPPORT_H - 1) break;

                            // Кликаем по слайдеру
                            int barPadRight = 8 + opt.getFormatted().length() * 5 + 4;
                            int bx = modX + 9;
                            int bw = modW - 9 - barPadRight;
                            int by = curY + 16;
                            if (inside(mx, my, bx, by - 3, bw, 10)) {
                                float t = (float)(mx - bx) / bw;
                                opt.setValue(opt.getMin() + t * (opt.getMax() - opt.getMin()));
                                draggingSlider = opt;
                                draggingModule = m;   // ← запоминаем модуль
                                sliderBarX = bx;
                                sliderBarW = bw;
                                return true;
                            }
                            curY += OPT_H;
                        }
                    }
                }
            }
            return super.mouseClicked(event, stillValid);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            // Перетаскивание слайдера
            if (draggingSlider != null && event.button() == 0) {
                float t = (float)(event.x() - sliderBarX) / sliderBarW;
                draggingSlider.setValue(draggingSlider.getMin() +
                        t * (draggingSlider.getMax() - draggingSlider.getMin()));
                // Применяем изменения, если модуль активен и является Fullbright
                if (draggingModule != null) {
                    draggingModule.applyOptions();
                }
                return true;
            }
            // Перетаскивание окна
            if (dragging && event.button() == 0) {
                panelX = (int)(event.x() - dragOffX);
                panelY = (int)(event.y() - dragOffY);
                return true;
            }
            return super.mouseDragged(event, dx, dy);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            dragging = false;
            draggingSlider = null;
            draggingModule = null;   // сбрасываем
            return super.mouseReleased(event);
        }

        // ── Утилиты ─────────────────────────────────────────────────
        private void openSupportUrl() {
            try {
                Screen.clickUrlAction(this.minecraft, this, new URI(SUPPORT_URL));
            } catch (Exception ex) {
                try { java.awt.Desktop.getDesktop().browse(new URI(SUPPORT_URL)); }
                catch (Exception ignored) {}
            }
        }

        private boolean inside(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}