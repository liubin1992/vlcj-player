package uk.co.caprica.vlcjplayer.event;

import lombok.extern.slf4j.Slf4j;
import uk.co.caprica.vlcj.player.renderer.RendererItem;

/**
 * 渲染器添加事件
 */
@Slf4j
public final class RendererAddedEvent {

    private final RendererItem rendererItem;

    public RendererAddedEvent(RendererItem rendererItem) {
        log.info("渲染的项目：{}", rendererItem.name());
        this.rendererItem = rendererItem;
    }

    public RendererItem rendererItem() {
        return rendererItem;
    }

}
