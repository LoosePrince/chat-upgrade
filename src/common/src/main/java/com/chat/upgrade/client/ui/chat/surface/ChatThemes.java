package com.chat.upgrade.client.ui.chat.surface;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

public final class ChatThemes {
    private static final ChatTheme MODERN_BUBBLE = new ChatTheme(
            ChatSurfaceThemeId.MODERN_BUBBLE,
            tokens(
                    new ChatThemeTokens.Surface(
                            0xE612141A, 0xFF526176, 0xF01A1E27, 0xC80B0D12,
                            0xED171B23, 0xFF343D4D, 0xFFF0F3F8, 0xFF9AA6B7,
                            0xFFFFC76B, 0xD8181510, 0xFF8F6A2C, 0xFF718097),
                    new ChatThemeTokens.Message(
                            0xB82B3547, 0xD05D7598, 0xA51D2530, 0xC04C5B70,
                            0xB83A3220, 0xD29B7A35, 0xB83B2024, 0xD29D4C55,
                            0xB8233042, 0xD05D82B0, 0xA525272D, 0xB05F6570,
                            0xFFF2F5FA, 0xFFD7DEE9, 0xFFC8D9F0, 0xFFA3A8B0),
                    modernMedia(),
                    new ChatThemeTokens.Scrollbar(0xAA526176, 0x60343D4D, 0xCCCB3A33)),
            new ChatLayoutPolicy(
                    ChatLayoutPolicy.MessageDecoration.BUBBLE,
                    ChatLayoutPolicy.IdentityPresentation.GROUP_START,
                    3, 6, 2, 24, 18, 4, 90));

    private static final ChatTheme COMPACT_FEED = new ChatTheme(
            ChatSurfaceThemeId.COMPACT_FEED,
            tokens(
                    new ChatThemeTokens.Surface(
                            0xE30C0F12, 0xFF39434C, 0xED12171B, 0xB807090B,
                            0xE814191D, 0xFF293138, 0xFFE7ECEF, 0xFF91A0AA,
                            0xFFFFC45C, 0xD816130D, 0xFF806127, 0xFF60717C),
                    new ChatThemeTokens.Message(
                            0x2E1D252B, 0xCC5C879A, 0x321A2024, 0xB6576872,
                            0x3D302712, 0xCDA67A32, 0x3D34181A, 0xCDA34C52,
                            0x361B2B32, 0xC95A8798, 0x3023262A, 0xA65F6870,
                            0xFFE6EDF1, 0xFFC9D2D8, 0xFFBDD7E0, 0xFF969FA5),
                    new ChatThemeTokens.Media(
                            0xB31B2024, 0x80171C20, 0x80361619,
                            0xFFE1E8EC, 0xFFB7C2C9, 0xFFFF858B,
                            0xFF30393F, 0xFF416978, 0xFF48545B,
                            0xFF65BDD4, 0xD21B2024),
                    new ChatThemeTokens.Scrollbar(0xA85C7986, 0x502A3338, 0xC9D06A4F)),
            new ChatLayoutPolicy(
                    ChatLayoutPolicy.MessageDecoration.FEED_STRIPE,
                    ChatLayoutPolicy.IdentityPresentation.GROUP_START,
                    1, 3, 0, 19, 14, 2, 100));

    private static final ChatTheme NATIVE_ENHANCED = new ChatTheme(
            ChatSurfaceThemeId.NATIVE_ENHANCED,
            tokens(
                    new ChatThemeTokens.Surface(
                            0xD8101010, 0xFF7F7F7F, 0xE0181818, 0xA0000000,
                            0xDF151515, 0xFF555555, 0xFFFFFFFF, 0xFFAAAAAA,
                            0xFFFFFF55, 0xD8101008, 0xFF8F6A2C, 0xFFAAAAAA),
                    new ChatThemeTokens.Message(
                            0x82000000, 0xB87F7F7F, 0x82000000, 0xB8555555,
                            0x8A332B12, 0xC6AA7F2A, 0x8A351718, 0xC6AA4444,
                            0x82131F2A, 0xB8678BB0, 0x82171717, 0xA8666666,
                            0xFFFFFFFF, 0xFFDDDDDD, 0xFFBFD8F0, 0xFFAAAAAA),
                    legacyMedia(),
                    new ChatThemeTokens.Scrollbar(0xAA33333A, 0x60666666, 0xCCCB3A33)),
            new ChatLayoutPolicy(
                    ChatLayoutPolicy.MessageDecoration.NATIVE_CARD,
                    ChatLayoutPolicy.IdentityPresentation.EVERY_PLAYER_MESSAGE,
                    2, 4, 1, 21, 16, 2, 100));

    private static final Map<ChatSurfaceThemeId, ChatTheme> THEMES = registry();

    private ChatThemes() {
    }

    public static ChatTheme resolve(@Nullable String serializedId) {
        return resolve(ChatSurfaceThemeId.parse(serializedId));
    }

    public static ChatTheme resolve(@Nullable ChatSurfaceThemeId id) {
        ChatSurfaceThemeId safeId = id == null ? ChatSurfaceThemeId.DEFAULT : id;
        return THEMES.getOrDefault(safeId, MODERN_BUBBLE);
    }

    public static ChatTheme compatibility() {
        return NATIVE_ENHANCED;
    }

    private static Map<ChatSurfaceThemeId, ChatTheme> registry() {
        EnumMap<ChatSurfaceThemeId, ChatTheme> themes = new EnumMap<>(ChatSurfaceThemeId.class);
        themes.put(MODERN_BUBBLE.id(), MODERN_BUBBLE);
        themes.put(COMPACT_FEED.id(), COMPACT_FEED);
        themes.put(NATIVE_ENHANCED.id(), NATIVE_ENHANCED);
        return Map.copyOf(themes);
    }

    private static ChatThemeTokens tokens(
            ChatThemeTokens.Surface surface,
            ChatThemeTokens.Message message,
            ChatThemeTokens.Media media,
            ChatThemeTokens.Scrollbar scrollbar) {
        return new ChatThemeTokens(
                surface,
                message,
                new ChatThemeTokens.Identity(0xFFDDE7F5, 0xB3FFFFFF),
                media,
                scrollbar);
    }

    private static ChatThemeTokens.Media modernMedia() {
        return new ChatThemeTokens.Media(
                0xD21C1C20, 0x80181A1F, 0x80281212,
                0xFFD7DCE6, 0xFFD2D2D7, 0xFFFF7878,
                0xFF3A3E48, 0xFF4C6284, 0xFF444852,
                0xFF64C8FF, 0xD91C1C20);
    }

    private static ChatThemeTokens.Media legacyMedia() {
        return new ChatThemeTokens.Media(
                0xD91C1C20, 0x80181A1F, 0x80281212,
                0xFFD7DCE6, 0xFFD2D2D7, 0xFFFF7878,
                0xFF3A3E48, 0xFF4C6284, 0xFF444852,
                0xFF64C8FF, 0xD91C1C20);
    }
}