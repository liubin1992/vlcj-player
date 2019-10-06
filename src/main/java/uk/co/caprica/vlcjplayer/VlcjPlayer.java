/*
 * This file is part of VLCJ.
 *
 * VLCJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VLCJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VLCJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2015 Caprica Software Limited.
 */

package uk.co.caprica.vlcjplayer;

import com.sun.jna.NativeLibrary;
import lombok.extern.slf4j.Slf4j;
import uk.co.caprica.nativestreams.NativeStreams;
import uk.co.caprica.vlcj.binding.LibC;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;
import uk.co.caprica.vlcj.player.renderer.RendererDiscoverer;
import uk.co.caprica.vlcj.player.renderer.RendererDiscovererDescription;
import uk.co.caprica.vlcj.player.renderer.RendererDiscovererEventListener;
import uk.co.caprica.vlcj.player.renderer.RendererItem;
import uk.co.caprica.vlcj.support.Info;
import uk.co.caprica.vlcj.binding.RuntimeUtil;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.log.NativeLog;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.fullscreen.exclusivemode.ExclusiveModeFullScreenStrategy;
import uk.co.caprica.vlcjplayer.event.AfterExitFullScreenEvent;
import uk.co.caprica.vlcjplayer.event.BeforeEnterFullScreenEvent;
import uk.co.caprica.vlcjplayer.event.RendererAddedEvent;
import uk.co.caprica.vlcjplayer.event.RendererDeletedEvent;
import uk.co.caprica.vlcjplayer.event.ShutdownEvent;
import uk.co.caprica.vlcjplayer.view.debug.DebugFrame;
import uk.co.caprica.vlcjplayer.view.effects.EffectsFrame;
import uk.co.caprica.vlcjplayer.view.main.MainFrame;
import uk.co.caprica.vlcjplayer.view.messages.NativeLogFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import static uk.co.caprica.vlcjplayer.Application.application;

/**
 * Application entry-point.
 */
@Slf4j
public class VlcjPlayer implements RendererDiscovererEventListener {

    static {
//        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "/disks/data/build/install/libs-3");
//        LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", "/disks/data/build/install/libs-3/plugins", 1);
    }

    private static VlcjPlayer app;

    private static final NativeStreams nativeStreams;

    // Redirect the native output streams to files, useful since VLC can generate a lot of noisy native logs we don't care about
    // (on the other hand, if we don't look at the logs we might won't see errors)
    static {
//        if (RuntimeUtil.isNix()) {
//            nativeStreams = new NativeStreams("stdout.log", "stderr.log");
//        }
//        else {
            nativeStreams = null;
//        }
    }

    private final List<RendererDiscoverer> rendererDiscoverers = new ArrayList<>();

    private final JFrame mainFrame;

    @SuppressWarnings("unused")
    private final JFrame messagesFrame;

    @SuppressWarnings("unused")
    private final JFrame effectsFrame;

    @SuppressWarnings("unused")
    private final JFrame debugFrame;

    private final NativeLog nativeLog;

    public static void main(String[] args) throws InterruptedException {
        //应用程序版本/环境信息。 
        //可能对诊断是有用的。
        Info info = Info.getInstance();
        log.info("vlcj             : {}", info.vlcjVersion() != null ? info.vlcjVersion() : "<version not available>");
        log.info("os               : {}", val(info.os()));
        log.info("java             : {}", val(info.javaVersion()));
        log.info("java.home        : {}", val(info.javaHome()));
        log.info("jna.library.path : {}", val(info.jnaLibraryPath()));
        log.info("java.library.path: {}", val(info.javaLibraryPath()));
        log.info("PATH             : {}", val(info.path()));
        log.info("VLC_PLUGIN_PATH  : {}", val(info.pluginPath()));

        if (RuntimeUtil.isNix()) {
            log.info("LD_LIBRARY_PATH  : {}", val(info.ldLibraryPath()));
        } else if (RuntimeUtil.isMac()) {
            log.info("DYLD_LIBRARY_PATH          : {}", val(info.dyldLibraryPath()));
            log.info("DYLD_FALLBACK_LIBRARY_PATH : {}", val(info.dyldFallbackLibraryPath()));
        }
        //设置外观感觉
        setLookAndFeel();

//        SwingUtilities.invokeLater(new Runnable() {
//            @Override
//            public void run() {
                app = new VlcjPlayer();
                app.start();
//            }
//        });
    }

    private static String val(String val) {
        return val != null ? val : "<not set>";
    }

    /**
     * 设置外观风格
     */
    private static void setLookAndFeel() {
        log.info("系统名称：{}", System.getProperty("os.name"));
        String lookAndFeelClassName;
        if (RuntimeUtil.isNix()) {
            lookAndFeelClassName = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
        }
        else {
            lookAndFeelClassName = UIManager.getSystemLookAndFeelClassName();
        }
        log.info("外观风格类名称：{}   {}", lookAndFeelClassName, "UIManager.setLookAndFeel(lookAndFeelClassName)");
        try {
            UIManager.setLookAndFeel(lookAndFeelClassName);
        }
        catch(Exception e) {
            log.error(e.getMessage(),"Silently fail, it doesn't matter", e);
        }
    }

    /**
     * Vlc播放器构造方法
     */
    public VlcjPlayer() {
        //内置播放器组件
        EmbeddedMediaPlayerComponent mediaPlayerComponent = application().mediaPlayerComponent();
        CallbackMediaPlayerComponent callbackMediaPlayerComponent = application().callbackMediaPlayerComponent();

        prepareDiscoverers();

        mainFrame = new MainFrame();
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Stop audio as soon as possible
                application().mediaPlayer().controls().stop();

                // Avoid the visible delay disposing everything
                mainFrame.setVisible(false);

                for (RendererDiscoverer discoverer : rendererDiscoverers) {
                    discoverer.stop();
                }

                mediaPlayerComponent.release();
                callbackMediaPlayerComponent.release();

                if (nativeStreams != null) {
                    nativeStreams.release();
                }

                application().post(ShutdownEvent.INSTANCE);
            }

            @Override
            public void windowClosed(WindowEvent e) {
            }
        });
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        application().mediaPlayerComponent().mediaPlayer().fullScreen().strategy(new VlcjPlayerFullScreenStrategy(mainFrame));
        application().callbackMediaPlayerComponent().mediaPlayer().fullScreen().strategy(new VlcjPlayerFullScreenStrategy(mainFrame));

        nativeLog = mediaPlayerComponent.mediaPlayerFactory().application().newLog();

        messagesFrame = new NativeLogFrame(nativeLog);
        effectsFrame = new EffectsFrame();
        debugFrame = new DebugFrame();
    }

    private void start() {
        mainFrame.setVisible(true);

        for (RendererDiscoverer discoverer : rendererDiscoverers) {
            log.info("渲染发现者{}", discoverer.events().getClass().getName());
            discoverer.start();
        }
    }

    private void prepareDiscoverers() {
        EmbeddedMediaPlayerComponent mediaPlayerComponent = application().mediaPlayerComponent();
        for (RendererDiscovererDescription descriptions : mediaPlayerComponent.mediaPlayerFactory().renderers().discoverers()) {
            log.info("渲染发现者描述, 名称{}  {}", descriptions.name(), descriptions.toString());
            RendererDiscoverer discoverer = mediaPlayerComponent.mediaPlayerFactory().renderers().discoverer(descriptions.name());
            discoverer.events().addRendererDiscovererEventListener(this);
            rendererDiscoverers.add(discoverer);
        }
    }

    @Override
    public void rendererDiscovererItemAdded(RendererDiscoverer rendererDiscoverer, RendererItem itemAdded) {
        log.info("渲染发现者添加项,rendererDiscoverer{} {}", rendererDiscoverer.toString(), itemAdded.iconUri());
        application().post(new RendererAddedEvent(itemAdded));
    }

    @Override
    public void rendererDiscovererItemDeleted(RendererDiscoverer rendererDiscoverer, RendererItem itemDeleted) {
        application().post(new RendererDeletedEvent(itemDeleted));
    }

}
