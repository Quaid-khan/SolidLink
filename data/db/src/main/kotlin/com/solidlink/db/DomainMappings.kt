package com.solidlink.db

import com.solidlink.common.OpaqueId
import com.solidlink.domain.Checkpoint
import com.solidlink.domain.ChunkState
import com.solidlink.domain.ChunkStatus
import com.solidlink.domain.DeviceIdentity
import com.solidlink.domain.DiagnosticEvent
import com.solidlink.domain.DiagnosticSeverity
import com.solidlink.domain.ExportJob
import com.solidlink.domain.ExportStatus
import com.solidlink.domain.PeerRecord
import com.solidlink.domain.SecurityPolicy
import com.solidlink.domain.SessionRole
import com.solidlink.domain.SessionStatus
import com.solidlink.domain.ShareSession
import com.solidlink.domain.TransferBatch
import com.solidlink.domain.TransferDirection
import com.solidlink.domain.TransferObject
import com.solidlink.domain.TransferPolicy
import com.solidlink.domain.TransferStatus
import com.solidlink.domain.TrustState
import java.time.Instant

internal fun ShareSession.toEntity(): ShareSessionEntity = ShareSessionEntity(
    sessionId = sessionId.value,
    role = role.name,
    status = status.name,
    createdAtEpochMs = createdAt.toEpochMilli(),
    expiresAtEpochMs = expiresAt.toEpochMilli(),
    hostCodeHash = hostCodeHash,
    advertisedName = advertisedName,
    transportHints = StringCodec.encodeSet(transportHints),
    securityPolicy = "${securityPolicy.requirePeerApproval},${securityPolicy.allowAdvancedSas},${securityPolicy.localOnlyRequired}",
)

internal fun ShareSessionEntity.toDomain(): ShareSession = ShareSession(
    sessionId = OpaqueId(sessionId),
    role = SessionRole.valueOf(role),
    status = SessionStatus.valueOf(status),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    expiresAt = Instant.ofEpochMilli(expiresAtEpochMs),
    hostCodeHash = hostCodeHash,
    advertisedName = advertisedName,
    transportHints = StringCodec.decodeSet(transportHints),
    securityPolicy = securityPolicy.split(',').let {
        SecurityPolicy(
            requirePeerApproval = it.getOrElse(0) { "true" }.toBoolean(),
            allowAdvancedSas = it.getOrElse(1) { "true" }.toBoolean(),
            localOnlyRequired = it.getOrElse(2) { "true" }.toBoolean(),
        )
    },
)

internal fun PeerRecord.toEntity(): PeerRecordEntity = PeerRecordEntity(
    peerId = peerId.value,
    sessionId = sessionId.value,
    displayName = displayName,
    transport = transport,
    endpointMetadata = StringCodec.encodeMap(endpointMetadata),
    capabilities = StringCodec.encodeSet(capabilities),
    trustState = trustState.name,
    lastSeenAtEpochMs = lastSeenAt.toEpochMilli(),
)

internal fun PeerRecordEntity.toDomain(): PeerRecord = PeerRecord(
    peerId = OpaqueId(peerId),
    sessionId = OpaqueId(sessionId),
    displayName = displayName,
    transport = transport,
    endpointMetadata = StringCodec.decodeMap(endpointMetadata),
    capabilities = StringCodec.decodeSet(capabilities),
    trustState = TrustState.valueOf(trustState),
    lastSeenAt = Instant.ofEpochMilli(lastSeenAtEpochMs),
)

internal fun TransferBatch.toEntity(): TransferBatchEntity = TransferBatchEntity(
    batchId = batchId.value,
    sessionId = sessionId.value,
    direction = direction.name,
    status = status.name,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
    allowMultipleReceivers = policy.allowMultipleReceivers,
    maxInFlightChunks = policy.maxInFlightChunks,
    maxInFlightBytes = policy.maxInFlightBytes,
    totalBytes = totalBytes,
    verifiedBytes = verifiedBytes,
)

internal fun TransferBatchEntity.toDomain(): TransferBatch = TransferBatch(
    batchId = OpaqueId(batchId),
    sessionId = OpaqueId(sessionId),
    direction = TransferDirection.valueOf(direction),
    status = TransferStatus.valueOf(status),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    policy = TransferPolicy(allowMultipleReceivers, maxInFlightChunks, maxInFlightBytes),
    totalBytes = totalBytes,
    verifiedBytes = verifiedBytes,
)

internal fun TransferObject.toEntity(): TransferObjectEntity = TransferObjectEntity(
    objectId = objectId.value,
    batchId = batchId.value,
    peerId = peerId.value,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    chunkSize = chunkSize,
    chunkCount = chunkCount,
    sourceUri = sourceUri,
    stagingPath = stagingPath,
    finalDigest = finalDigest,
    status = status.name,
    finalVerified = finalVerified,
    committed = committed,
)

internal fun TransferObjectEntity.toDomain(): TransferObject = TransferObject(
    objectId = OpaqueId(objectId),
    batchId = OpaqueId(batchId),
    peerId = OpaqueId(peerId),
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    chunkSize = chunkSize,
    chunkCount = chunkCount,
    sourceUri = sourceUri,
    stagingPath = stagingPath,
    finalDigest = finalDigest,
    status = TransferStatus.valueOf(status),
    finalVerified = finalVerified,
    committed = committed,
)

internal fun ChunkState.toEntity(): ChunkStateEntity = ChunkStateEntity(
    objectId = objectId.value,
    chunkIndex = chunkIndex,
    offset = offset,
    length = length,
    digest = digest,
    state = state.name,
    attempts = attempts,
    durableAtEpochMs = durableAt?.toEpochMilli(),
)

internal fun ChunkStateEntity.toDomain(): ChunkState = ChunkState(
    objectId = OpaqueId(objectId),
    chunkIndex = chunkIndex,
    offset = offset,
    length = length,
    digest = digest,
    state = ChunkStatus.valueOf(state),
    attempts = attempts,
    durableAt = durableAtEpochMs?.let(Instant::ofEpochMilli),
)

internal fun Checkpoint.toEntity(): CheckpointEntity = CheckpointEntity(
    objectId = objectId.value,
    highestDurableSequence = highestDurableSequence,
    verifiedRanges = StringCodec.encodeRanges(verifiedRanges),
    stagingLength = stagingLength,
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

internal fun CheckpointEntity.toDomain(): Checkpoint = Checkpoint(
    objectId = OpaqueId(objectId),
    highestDurableSequence = highestDurableSequence,
    verifiedRanges = StringCodec.decodeRanges(verifiedRanges),
    stagingLength = stagingLength,
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

internal fun DeviceIdentity.toEntity(): DeviceIdentityEntity = DeviceIdentityEntity(
    identityId = identityId.value,
    publicKey = publicKey,
    label = label,
    createdAtEpochMs = createdAt.toEpochMilli(),
    trustState = trustState.name,
)

internal fun DeviceIdentityEntity.toDomain(): DeviceIdentity = DeviceIdentity(
    identityId = OpaqueId(identityId),
    publicKey = publicKey,
    label = label,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    trustState = TrustState.valueOf(trustState),
)

internal fun ExportJob.toEntity(): ExportJobEntity = ExportJobEntity(
    objectId = objectId.value,
    destinationUri = destinationUri,
    status = status.name,
    bytesWritten = bytesWritten,
    errorCode = errorCode,
    createdAtEpochMs = createdAt.toEpochMilli(),
)

internal fun ExportJobEntity.toDomain(): ExportJob = ExportJob(
    objectId = OpaqueId(objectId),
    destinationUri = destinationUri,
    status = ExportStatus.valueOf(status),
    bytesWritten = bytesWritten,
    errorCode = errorCode,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
)

internal fun DiagnosticEvent.toEntity(): DiagnosticEventEntity = DiagnosticEventEntity(
    eventId = eventId.value,
    sessionId = sessionId?.value,
    category = category,
    code = code,
    severity = severity.name,
    safeMessage = safeMessage,
    createdAtEpochMs = createdAt.toEpochMilli(),
)

internal fun DiagnosticEventEntity.toDomain(): DiagnosticEvent = DiagnosticEvent(
    eventId = OpaqueId(eventId),
    sessionId = sessionId?.let(::OpaqueId),
    category = category,
    code = code,
    severity = DiagnosticSeverity.valueOf(severity),
    safeMessage = safeMessage,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
)

internal object StringCodec {
    private const val ITEM_SEPARATOR = '\u001F'
    private const val KEY_VALUE_SEPARATOR = '\u001E'

    fun encodeSet(values: Set<String>): String = values.toList().sorted().joinToString(ITEM_SEPARATOR.toString()) { escape(it) }

    fun decodeSet(value: String): Set<String> = if (value.isEmpty()) emptySet() else value.split(ITEM_SEPARATOR).map(::unescape).toSet()

    fun encodeMap(values: Map<String, String>): String = values.toList().sortedBy { it.first }
        .joinToString(ITEM_SEPARATOR.toString()) { "${escape(it.first)}$KEY_VALUE_SEPARATOR${escape(it.second)}" }

    fun decodeMap(value: String): Map<String, String> = if (value.isEmpty()) emptyMap() else value.split(ITEM_SEPARATOR).associate {
        val pair = it.split(KEY_VALUE_SEPARATOR, limit = 2)
        unescape(pair.first()) to unescape(pair.getOrElse(1) { "" })
    }

    fun encodeRanges(ranges: List<LongRange>): String = ranges.joinToString(ITEM_SEPARATOR.toString()) { "${it.first}:${it.last}" }

    fun decodeRanges(value: String): List<LongRange> = if (value.isEmpty()) emptyList() else value.split(ITEM_SEPARATOR).map {
        val pair = it.split(':', limit = 2)
        pair[0].toLong()..pair[1].toLong()
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace(ITEM_SEPARATOR.toString(), "\\u001F").replace(KEY_VALUE_SEPARATOR.toString(), "\\u001E")

    private fun unescape(value: String): String = value.replace("\\u001F", ITEM_SEPARATOR.toString()).replace("\\u001E", KEY_VALUE_SEPARATOR.toString()).replace("\\\\", "\\")
}
