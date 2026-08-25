package com.solidlink.common

import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Random, opaque identifier with no user or device-derived meaning. */
@JvmInline
public value class OpaqueId(public val value: String) {
    public companion object {
        public fun random(): OpaqueId = OpaqueId(UUID.randomUUID().toString())
    }
}

public interface TimeSource {
    public fun now(): Instant
}

public class SystemTimeSource(
    private val clock: Clock = Clock.systemUTC(),
) : TimeSource {
    override fun now(): Instant = clock.instant()
}
