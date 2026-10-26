package dev.casperrs.cookieCore.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class SidebarDiffTest {

    /** Records every write the diff makes, in order, as plain text. */
    private static final class Recording implements SidebarSink {
        final List<String> calls = new ArrayList<>();

        @Override
        public void title(Component title) {
            calls.add("title:" + plain(title));
        }

        @Override
        public void resize(int count) {
            calls.add("resize:" + count);
        }

        @Override
        public void line(int index, Component text) {
            calls.add("line" + index + ":" + plain(text));
        }

        private static String plain(Component c) {
            return PlainTextComponentSerializer.plainText().serialize(c);
        }
    }

    private static Component text(String s) {
        return Component.text(s);
    }

    private static List<Component> lines(String... s) {
        return Arrays.stream(s).map(SidebarDiffTest::text).toList();
    }

    @Test
    void firstApplyWritesTheCountTitleAndEveryLineAndAnIdenticalSecondApplySendsNothing() {
        Recording sink = new Recording();
        SidebarDiff diff = new SidebarDiff();
        assertEquals(4, diff.apply(sink, text("T"), lines("a", "b")));
        assertEquals(List.of("resize:2", "title:T", "line0:a", "line1:b"), sink.calls);

        sink.calls.clear();
        assertEquals(0, diff.apply(sink, text("T"), lines("a", "b")));
        assertTrue(sink.calls.isEmpty(), sink.calls.toString());
    }

    @Test
    void oneChangedLineSendsExactlyOneWrite() {
        Recording sink = new Recording();
        SidebarDiff diff = new SidebarDiff();
        diff.apply(sink, text("T"), lines("a", "b", "c"));
        sink.calls.clear();

        assertEquals(1, diff.apply(sink, text("T"), lines("a", "x", "c")));
        assertEquals(List.of("line1:x"), sink.calls);
    }

    /** A changed line count resizes the objective and rewrites every line, because the resize clears the old ones. */
    @Test
    void aChangedLineCountResizesAndRewritesEveryLine() {
        Recording sink = new Recording();
        SidebarDiff diff = new SidebarDiff();
        diff.apply(sink, text("T"), lines("a", "b"));
        sink.calls.clear();

        assertEquals(4, diff.apply(sink, text("T"), lines("a", "b", "c")), "resize plus three lines");
        assertEquals(List.of("resize:3", "line0:a", "line1:b", "line2:c"), sink.calls);

        sink.calls.clear();
        assertEquals(2, diff.apply(sink, text("T"), lines("a")));
        assertEquals(List.of("resize:1", "line0:a"), sink.calls);
    }

    @Test
    void aChangedTitleAloneIsTheOnlyWrite() {
        Recording sink = new Recording();
        SidebarDiff diff = new SidebarDiff();
        diff.apply(sink, text("T"), lines("a"));
        sink.calls.clear();

        assertEquals(1, diff.apply(sink, text("U"), lines("a")));
        assertEquals(List.of("title:U"), sink.calls);
    }
}
