package com.github.mrzhqiang.maplestory.di;

import com.google.inject.AbstractModule;

/**
 * Network module — provides Netty-based networking dependencies.
 * Previously provided MINA-specific bindings (NioSocketAcceptor, ProtocolCodecFilter)
 * which have been removed as part of the MINA→Netty migration.
 */
final class NettyModule extends AbstractModule {

    static final NettyModule INSTANCE = new NettyModule();

    // All Netty-based handlers use @Inject on their constructors.
    // Guice just-in-time binding handles them automatically.
    // Channel-specific objects (encoder, decoder) are created per-channel in ChannelInitializer.
}
