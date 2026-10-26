package dev.casperrs.cookieCore.scoreboard;

import net.kyori.adventure.text.Component;

/** Where {@link SidebarDiff} writes changes. Each call is one update sent to the player. */
interface SidebarSink {

    void title(Component title);

    /** The sidebar now has {@code count} lines; line {@code i} sorts above line {@code i + 1}. Every line is written again afterwards. */
    void resize(int count);

    void line(int index, Component text);
}
