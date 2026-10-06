package dev.faspadio.pvsvcbridge.client.connection;

import dev.faspadio.pvsvcbridge.PvSvcBridge;
import dev.faspadio.pvsvcbridge.client.connection.v1_21_11.SimpleVoiceChatPayloads;
import dev.faspadio.pvsvcbridge.client.PlasmoVoiceSettings;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import org.concentus.OpusApplication;
import org.concentus.OpusDecoder;
import org.concentus.OpusEncoder;
import org.concentus.OpusException;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.device.AlContextOutputDevice;
import su.plo.voice.api.client.audio.device.DeviceException;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.audio.source.LoopbackSource;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public final class SimpleVoiceChatTransport implements AutoCloseable {

    private static final int MAGIC = 0xFF;
    private static final int COMPATIBILITY_VERSION = 20;
    private static final int SAMPLE_RATE = 48_000;
    private static final int CHANNELS = 1;
    private static final int FRAME_SAMPLES = 960;
    private static final long FRAME_DURATION_NANOS = FRAME_SAMPLES * 1_000_000_000L / SAMPLE_RATE;
    private static final int MAX_PACKET_BYTES = 2048;
    private static final int MAX_AUDIO_CHANNELS = 256;
    private static final int MAX_DISCOVERED_SPEAKERS = 256;
    private static final int READ_TIMEOUT_MS = 200;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SimpleVoiceChatPayloads.SecretResponse server;
    private final DatagramSocket socket;
    private final SecretKeySpec key;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Set<UUID> discoveredSpeakers = ConcurrentHashMap.newKeySet();
    private final Thread receiverThread;
    private volatile boolean pushToTalk;
    private volatile boolean authenticated;
    private volatile boolean connected;
    private volatile boolean voiceActivation;
    private volatile boolean microphoneTest;
    private volatile boolean voiceTransmitting;
    private final long createdAt = System.currentTimeMillis();
    private volatile long lastKeepAlive;
    private volatile long lastHandshake;
    private volatile long connectionCheckStartedAt;
    private long micSequence;
    private volatile MicrophoneCapture microphone;
    private volatile Playback playback;
    private volatile SpatialSnapshot spatialSnapshot = SpatialSnapshot.EMPTY;

    private SimpleVoiceChatTransport(SimpleVoiceChatPayloads.SecretResponse server, VoiceEndpoint endpoint)
            throws IOException {
        this.server = server;
        this.socket = new DatagramSocket();
        this.socket.connect(endpoint.address(), endpoint.port());
        this.socket.setSoTimeout(READ_TIMEOUT_MS);
        this.key = new SecretKeySpec(server.secret().clone(), "AES");
        BridgeLog.info(
                "Connecting to Simple Voice Chat UDP endpoint {}:{}",
                endpoint.address().getHostAddress(),
                endpoint.port()
        );
        this.receiverThread = new Thread(this::receiveLoop, "PV-SVC-Bridge-UDP");
        this.receiverThread.setDaemon(true);
        this.receiverThread.start();
    }

    public static SimpleVoiceChatTransport connect(
            SimpleVoiceChatPayloads.SecretResponse secret,
            SocketAddress minecraftServerAddress
    ) throws IOException {
        if (secret.serverPort() < 1 || secret.serverPort() > 65535) {
            throw new IOException("Simple Voice Chat supplied an invalid UDP port");
        }
        if (secret.secret().length != 16) {
            throw new IOException("Simple Voice Chat supplied an invalid encryption key");
        }
        if (secret.keepAliveMs() <= 0) {
            throw new IOException("Simple Voice Chat supplied an invalid keep-alive interval");
        }
        if (secret.codec() < 0 || secret.codec() > 2) {
            throw new IOException("Simple Voice Chat supplied an unsupported audio codec");
        }
        VoiceEndpoint endpoint = resolveEndpoint(
                secret.voiceHost(),
                minecraftServerAddress,
                secret.serverPort()
        );
        return new SimpleVoiceChatTransport(secret, endpoint);
    }

    public static int compatibilityVersion() {
        return COMPATIBILITY_VERSION;
    }

    public void setPushToTalk(boolean pressed) {
        if (!running.get()) {
            return;
        }
        if (pressed == pushToTalk) {
            return;
        }
        pushToTalk = pressed;
        updateMicrophoneCapture();
    }

    public void setVoiceActivation(boolean enabled) {
        if (voiceActivation == enabled) {
            return;
        }
        voiceActivation = enabled;
        updateMicrophoneCapture();
    }

    public void setMicrophoneTest(boolean enabled) {
        if (microphoneTest == enabled) {
            return;
        }
        microphoneTest = enabled;
        updateMicrophoneCapture();
    }

    public synchronized void refreshInputDevice() {
        MicrophoneCapture current = microphone;
        if (current != null) {
            microphone = null;
            current.close();
        }
        updateMicrophoneCapture();
    }

    public synchronized void refreshOutputDevice() {
        closePlayback();
        if (connected && running.get()) {
            startPlayback();
        }
    }

    public void refreshAudioDevices() {
        refreshInputDevice();
        refreshOutputDevice();
    }

    public boolean isConnected() {
        return connected && running.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPushToTalkActive() {
        MicrophoneCapture current = microphone;
        return connected && current != null && current.isReady() && (pushToTalk || voiceTransmitting);
    }

    public void updateSpatialSnapshot(SpatialSnapshot snapshot) {
        spatialSnapshot = snapshot;
    }

    private void noteSpeaker(UUID speaker) {
        if (discoveredSpeakers.contains(speaker) || discoveredSpeakers.size() < MAX_DISCOVERED_SPEAKERS) {
            discoveredSpeakers.add(speaker);
        }
    }

    public Set<UUID> drainDiscoveredSpeakers() {
        Set<UUID> speakers = Set.copyOf(discoveredSpeakers);
        discoveredSpeakers.removeAll(speakers);
        return speakers;
    }

    public record SpeakerPosition(double x, double y, double z) {
    }

    public record SpatialSnapshot(
            double listenerX,
            double listenerY,
            double listenerZ,
            float listenerYaw,
            Map<UUID, SpeakerPosition> speakers
    ) {
        private static final SpatialSnapshot EMPTY = new SpatialSnapshot(0, 0, 0, 0, Map.of());

        public SpatialSnapshot {
            speakers = Map.copyOf(speakers);
        }
    }

    private OpusApplication opusApplication() {
        return switch (server.codec()) {
            case 0 -> OpusApplication.OPUS_APPLICATION_VOIP;
            case 1 -> OpusApplication.OPUS_APPLICATION_AUDIO;
            case 2 -> OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY;
            default -> throw new IllegalStateException("Unsupported Simple Voice Chat codec");
        };
    }

    private static VoiceEndpoint resolveEndpoint(
            String voiceHost,
            SocketAddress serverAddress,
            int serverPort
    ) throws IOException {
        String host = minecraftHost(serverAddress);
        int port = serverPort;
        if (voiceHost != null && !voiceHost.isBlank()) {
            String suppliedHost = voiceHost.trim();
            try {
                int portOverride = Integer.parseInt(suppliedHost);
                if (portOverride < 1 || portOverride > 65535) {
                    throw new IOException("Simple Voice Chat supplied an invalid UDP port");
                }
                port = portOverride;
            } catch (NumberFormatException notPortOnly) {
                try {
                    URI voiceAddress = new URI("voicechat://" + suppliedHost);
                    if (voiceAddress.getHost() != null) {
                        host = voiceAddress.getHost();
                    }
                    if (voiceAddress.getPort() > 0) {
                        port = voiceAddress.getPort();
                    }
                } catch (URISyntaxException invalidAddress) {
                    throw new IOException("Invalid Simple Voice Chat address " + suppliedHost, invalidAddress);
                }
            }
        }
        if (port < 1 || port > 65535) {
            throw new IOException("Simple Voice Chat supplied an invalid UDP port");
        }
        try {
            return new VoiceEndpoint(InetAddress.getByName(host), port);
        } catch (UnknownHostException error) {
            throw new IOException("Could not resolve Simple Voice Chat host " + host, error);
        }
    }

    private static String minecraftHost(SocketAddress address) throws IOException {
        if (address instanceof InetSocketAddress inetAddress) {
            return inetAddress.getHostString();
        }
        throw new IOException("Could not determine the Minecraft server address for voice UDP");
    }

    private record VoiceEndpoint(InetAddress address, int port) {
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_BYTES];
        try {
            while (running.get()) {
                long now = System.currentTimeMillis();
                maintainHandshake(now);
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(datagram);
                } catch (SocketTimeoutException ignored) {
                    checkTimeout(now);
                    continue;
                }
                try {
                    handleDatagram(datagram.getData(), datagram.getOffset(), datagram.getLength());
                } catch (GeneralSecurityException | IllegalArgumentException error) {
                    BridgeLog.debug("Discarding an invalid Simple Voice Chat UDP packet", error);
                } catch (MalformedVoicePacketException error) {
                    BridgeLog.debug("Discarding a malformed Simple Voice Chat UDP packet", error);
                }
            }
        } catch (IOException | GeneralSecurityException error) {
            if (running.get()) {
                BridgeLog.error("Simple Voice Chat UDP connection failed", error);
            }
        } finally {
            connected = false;
            running.set(false);
            stopMicrophone();
            closePlayback();
            socket.close();
        }
    }

    private void maintainHandshake(long now) throws IOException, GeneralSecurityException {
        if (!authenticated) {
            if (now - createdAt > 30_000) {
                throw new IOException("Simple Voice Chat authentication timed out");
            }
            if (now - lastHandshake >= 1000) {
                sendPacket(5, uuidBytes(server.playerId(), server.secret()));
                lastHandshake = now;
            }
            return;
        }
        if (!connected) {
            if (connectionCheckStartedAt > 0 && now - connectionCheckStartedAt > 30_000) {
                throw new IOException("Simple Voice Chat connection check timed out");
            }
            if (now - lastHandshake >= 1000) {
                sendPacket(9, new byte[0]);
                lastHandshake = now;
            }
            return;
        }
        if (now - lastKeepAlive > server.keepAliveMs() * 10L) {
            throw new IOException("Simple Voice Chat UDP keep-alive timed out");
        }
    }

    private void checkTimeout(long now) throws IOException {
        if (connected && now - lastKeepAlive > server.keepAliveMs() * 10L) {
            throw new IOException("Simple Voice Chat UDP keep-alive timed out");
        }
    }

    private static byte[] uuidBytes(UUID playerId, byte[] secret) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + secret.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(playerId.getMostSignificantBits());
        buffer.putLong(playerId.getLeastSignificantBits());
        buffer.put(secret);
        return buffer.array();
    }

    private void handleDatagram(byte[] data, int offset, int length)
            throws IOException, GeneralSecurityException {
        ByteBuffer packet;
        int packetType;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN);
            if (buffer.remaining() < 2 || (buffer.get() & 0xFF) != MAGIC) {
                return;
            }
            int payloadLength = readVarInt(buffer);
            if (payloadLength < 1 || payloadLength > buffer.remaining()) {
                return;
            }
            byte[] encrypted = new byte[payloadLength];
            buffer.get(encrypted);
            byte[] plain = decrypt(encrypted);
            if (plain.length == 0) {
                throw new IOException("Simple Voice Chat sent an empty UDP packet");
            }
            packet = ByteBuffer.wrap(plain).order(ByteOrder.BIG_ENDIAN);
            packetType = packet.get() & 0xFF;
        } catch (IOException malformed) {
            throw new MalformedVoicePacketException(malformed);
        }
        switch (packetType) {
            case 6 -> {
                authenticated = true;
                connectionCheckStartedAt = System.currentTimeMillis();
                lastHandshake = 0;
                BridgeLog.info("Simple Voice Chat authenticated");
            }
            case 10 -> {
                if (!connected) {
                    connected = true;
                    lastKeepAlive = System.currentTimeMillis();
                    connectionCheckStartedAt = 0;
                    startPlayback();
                    updateMicrophoneCapture();
                    BridgeLog.info("Simple Voice Chat voice connection established");
                }
            }
            case 8 -> {
                lastKeepAlive = System.currentTimeMillis();
                sendPacket(8, new byte[0]);
            }
            case 2 -> {
                if (connected) {
                    try {
                        playRemoteAudio(packet);
                    } catch (IOException malformed) {
                        throw new MalformedVoicePacketException(malformed);
                    }
                }
            }
            case 3 -> {
                if (connected) {
                    try {
                        playGroupAudio(packet);
                    } catch (IOException malformed) {
                        throw new MalformedVoicePacketException(malformed);
                    }
                }
            }
            case 7 -> BridgeLog.debug("Ignoring Simple Voice Chat ping packet");
            default -> BridgeLog.debug("Ignoring Simple Voice Chat UDP packet type {}", packetType);
        }
    }

    private void playRemoteAudio(ByteBuffer packet) throws IOException {
        requireRemaining(packet, 16 + 16);
        UUID channelId = new UUID(packet.getLong(), packet.getLong());
        UUID sender = new UUID(packet.getLong(), packet.getLong());
        byte[] encoded = readByteArray(packet, MAX_PACKET_BYTES);
        requireRemaining(packet, 13);
        long sequence = packet.getLong();
        float distance = packet.getFloat();
        int flags = packet.get() & 0xFF;
        if ((flags & 0x02) != 0) {
            readString(packet, 16);
        }
        Playback current = playback;
        if (current != null) {
            noteSpeaker(sender);
            current.offer(channelId, new RemoteVoicePacket(
                    sender,
                    encoded,
                    sequence,
                    Float.isFinite(distance) ? Math.max(0, distance) : 0,
                    true,
                    System.nanoTime()
            ));
        }
    }

    private void playGroupAudio(ByteBuffer packet) throws IOException {
        requireRemaining(packet, 32);
        UUID channelId = new UUID(packet.getLong(), packet.getLong());
        UUID sender = new UUID(packet.getLong(), packet.getLong());
        byte[] encoded = readByteArray(packet, MAX_PACKET_BYTES);
        requireRemaining(packet, 9);
        long sequence = packet.getLong();
        int flags = packet.get() & 0xFF;
        if ((flags & 0x02) != 0) {
            readString(packet, 16);
        }
        Playback current = playback;
        if (current != null) {
            noteSpeaker(sender);
            current.offer(channelId, new RemoteVoicePacket(
                    sender,
                    encoded,
                    sequence,
                    0,
                    false,
                    System.nanoTime()
            ));
        }
    }

    private void sendPacket(int type, byte[] payload) throws IOException, GeneralSecurityException {
        ByteBuffer packet = ByteBuffer.allocate(1 + payload.length).order(ByteOrder.BIG_ENDIAN);
        packet.put((byte) type);
        packet.put(payload);
        byte[] encrypted = encrypt(packet.array());

        ByteBuffer datagram = ByteBuffer.allocate(1 + 16 + 5 + encrypted.length).order(ByteOrder.BIG_ENDIAN);
        datagram.put((byte) MAGIC);
        datagram.putLong(server.playerId().getMostSignificantBits());
        datagram.putLong(server.playerId().getLeastSignificantBits());
        writeVarInt(datagram, encrypted.length);
        datagram.put(encrypted);
        socket.send(new DatagramPacket(datagram.array(), datagram.position()));
    }

    private void sendMicrophonePacket(byte[] encoded, boolean whispering) {
        if (!connected) {
            return;
        }
        try {
            ByteArrayOutputStream packet = new ByteArrayOutputStream(encoded.length + 16);
            writeByteArray(packet, encoded);
            writeLong(packet, micSequence++);
            packet.write(whispering ? 1 : 0);
            sendPacket(1, packet.toByteArray());
        } catch (IOException | GeneralSecurityException error) {
            BridgeLog.error("Could not send a Simple Voice Chat microphone packet", error);
        }
    }

    private synchronized void updateMicrophoneCapture() {
        boolean shouldCapture = running.get() && connected && !PlasmoVoiceSettings.isInputCaptureDisabled();
        MicrophoneCapture current = microphone;
        if (shouldCapture && current == null) {
            current = new MicrophoneCapture(this);
            microphone = current;
            current.start();
        } else if (!shouldCapture && current != null) {
            microphone = null;
            current.close();
        }
    }

    private void stopMicrophone() {
        MicrophoneCapture current = microphone;
        microphone = null;
        if (current != null) {
            current.close();
        }
        voiceTransmitting = false;
    }

    private void startPlayback() {
        try {
            playback = new Playback(this);
        } catch (DeviceException | LineUnavailableException error) {
            BridgeLog.error("Could not open an audio output device for Simple Voice Chat", error);
        }
    }

    private void closePlayback() {
        Playback current = playback;
        playback = null;
        if (current != null) {
            current.close();
        }
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        pushToTalk = false;
        voiceActivation = false;
        microphoneTest = false;
        stopMicrophone();
        closePlayback();
        socket.close();
        receiverThread.interrupt();
    }

    private byte[] encrypt(byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain);
        ByteBuffer result = ByteBuffer.allocate(iv.length + encrypted.length);
        result.put(iv).put(encrypted);
        return result.array();
    }

    private byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        if (encrypted.length < 28) {
            throw new GeneralSecurityException("Encrypted voice packet is too short");
        }
        byte[] iv = new byte[12];
        byte[] data = new byte[encrypted.length - iv.length];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);
        System.arraycopy(encrypted, iv.length, data, 0, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return cipher.doFinal(data);
    }

    private static byte[] readByteArray(ByteBuffer buffer, int maximum) throws IOException {
        int length = readVarInt(buffer);
        if (length < 0 || length > maximum) {
            throw new IOException("Invalid voice packet data length");
        }
        requireRemaining(buffer, length);
        byte[] result = new byte[length];
        buffer.get(result);
        return result;
    }

    private static void readString(ByteBuffer buffer, int maximumBytes) throws IOException {
        int length = readVarInt(buffer);
        if (length < 0 || length > maximumBytes) {
            throw new IOException("Invalid voice packet string length");
        }
        requireRemaining(buffer, length);
        buffer.position(buffer.position() + length);
    }

    private static int readVarInt(ByteBuffer buffer) throws IOException {
        int result = 0;
        int position = 0;
        byte current;
        do {
            requireRemaining(buffer, 1);
            current = buffer.get();
            result |= (current & 0x7F) << position;
            position += 7;
            if (position > 35) {
                throw new IOException("VarInt is too long");
            }
        } while ((current & 0x80) != 0);
        return result;
    }

    private static void writeVarInt(ByteBuffer buffer, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            buffer.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buffer.put((byte) value);
    }

    private static void writeByteArray(ByteArrayOutputStream output, byte[] data) {
        writeVarInt(output, data.length);
        output.writeBytes(data);
    }

    private static void writeLong(ByteArrayOutputStream output, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            output.write((int) (value >>> shift) & 0xFF);
        }
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static void requireRemaining(ByteBuffer buffer, int count) throws IOException {
        if (count < 0 || buffer.remaining() < count) {
            throw new IOException("Truncated Simple Voice Chat packet");
        }
    }

    private static final class MalformedVoicePacketException extends IOException {

        private MalformedVoicePacketException(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }

    private static final class MicrophoneCapture extends Thread {

        private final SimpleVoiceChatTransport transport;
        private volatile boolean capturing = true;
        private volatile boolean ready;
        private volatile TargetDataLine line;
        private volatile InputDevice inputDevice;
        private volatile boolean transmitting;
        private OpusEncoder encoder;

        private MicrophoneCapture(SimpleVoiceChatTransport transport) {
            this.transport = transport;
            setDaemon(true);
            setName("PV-SVC-Bridge-Microphone");
        }

        @Override
        public void run() {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            try {
                inputDevice = PlasmoVoiceSettings.openInputDevice(format);
                if (inputDevice != null) {
                    inputDevice.start();
                } else {
                    line = (TargetDataLine) AudioSystem.getLine(info);
                    line.open(format, FRAME_SAMPLES * 4);
                    line.start();
                }
                encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, transport.opusApplication());
                ready = true;
                byte[] raw = new byte[FRAME_SAMPLES * 2];
                byte[] encoded = new byte[1275];
                while (capturing && transport.running.get()) {
                    short[] samples;
                    if (inputDevice != null) {
                        samples = inputDevice.read(FRAME_SAMPLES);
                        if (samples == null) {
                            LockSupport.parkNanos(1_000_000L);
                            continue;
                        }
                    } else {
                        int read = readFully(line, raw);
                        if (read != raw.length) {
                            continue;
                        }
                        samples = decodePcm(raw);
                    }
                    if (samples == null || samples.length != FRAME_SAMPLES) {
                        if (inputDevice != null && samples != null) {
                            LockSupport.parkNanos(1_000_000L);
                        }
                        continue;
                    }
                    short[] processed = inputDevice == null ? samples : inputDevice.processFilters(samples);
                    applyMicrophoneVolume(processed);
                    if (transport.microphoneTest && inputDevice != null) {
                        publishMicrophoneTest(inputDevice, samples, processed);
                    }

                    boolean shouldTransmit = !PlasmoVoiceSettings.isMicrophoneMuted()
                            && (transport.pushToTalk
                                    || transport.voiceActivation && isVoiceActivated(processed));
                    if (!shouldTransmit) {
                        if (transmitting) {
                            transport.sendMicrophonePacket(new byte[0], false);
                            transmitting = false;
                            transport.voiceTransmitting = false;
                        }
                        continue;
                    }
                    int encodedLength = encoder.encode(processed, 0, FRAME_SAMPLES, encoded, 0, encoded.length);
                    if (encodedLength > 0) {
                        byte[] frame = new byte[encodedLength];
                        System.arraycopy(encoded, 0, frame, 0, encodedLength);
                        transport.sendMicrophonePacket(frame, false);
                        transmitting = true;
                        transport.voiceTransmitting = true;
                    }
                }
            } catch (DeviceException | LineUnavailableException | OpusException error) {
                if (capturing && transport.running.get()) {
                    BridgeLog.error("Could not capture or encode microphone audio for Simple Voice Chat", error);
                }
            } catch (RuntimeException error) {
                if (capturing && transport.running.get()) {
                    BridgeLog.error("Microphone capture failed", error);
                }
            } finally {
                ready = false;
                if (transmitting) {
                    transport.sendMicrophonePacket(new byte[0], false);
                }
                transmitting = false;
                transport.voiceTransmitting = false;
                if (line != null) {
                    line.stop();
                    line.close();
                }
                if (inputDevice != null) {
                    PlasmoVoiceSettings.closeInputDevice(inputDevice);
                }
            }
        }

        private static short[] decodePcm(byte[] raw) {
            short[] samples = new short[FRAME_SAMPLES];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) ((raw[i * 2] & 0xFF) | (raw[i * 2 + 1] << 8));
            }
            return samples;
        }

        private static void applyMicrophoneVolume(short[] samples) {
            double volume = Math.max(0.0, Math.min(2.0, PlasmoVoiceSettings.microphoneVolume()));
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(samples[i] * volume)));
            }
        }

        private static boolean isVoiceActivated(short[] samples) {
            var client = PlasmoVoiceSettings.voiceClient();
            if (client == null) {
                return false;
            }
            var manager = client.getActivationManager();
            ClientActivation.Result parentResult = manager.getParentActivation()
                    .filter(activation -> !activation.isDisabled()
                            && activation.getType() != ClientActivation.Type.PUSH_TO_TALK)
                    .map(activation -> activation.process(samples, null))
                    .orElse(null);
            boolean activated = parentResult != null && parentResult.isActivated();
            for (ClientActivation activation : manager.getActivations()) {
                if (activation.isDisabled() || activation.getType() == ClientActivation.Type.PUSH_TO_TALK) {
                    continue;
                }
                ClientActivation.Result result = activation.process(
                        samples,
                        activation.getType() == ClientActivation.Type.INHERIT ? parentResult : null
                );
                if (result.isActivated()) {
                    activated = true;
                }
            }
            return activated;
        }

        private static void publishMicrophoneTest(InputDevice device, short[] raw, short[] processed) {
            var client = PlasmoVoiceSettings.voiceClient();
            if (client == null) {
                return;
            }
            short[] stereo = new short[processed.length * 2];
            for (int i = 0; i < processed.length; i++) {
                stereo[i * 2] = processed[i];
                stereo[i * 2 + 1] = processed[i];
            }
            AudioCaptureProcessedEvent event = new AudioCaptureProcessedEvent(
                    client.getAudioCapture(),
                    device,
                    raw,
                    new AudioCaptureProcessedEvent.ProcessedSamples() {
                        @Override
                        public short[] getMono() {
                            return processed;
                        }

                        @Override
                        public short[] getStereo() {
                            return stereo;
                        }
                    }
            );
            client.getEventBus().fire(event);
        }

        private static int readFully(TargetDataLine line, byte[] buffer) {
            int total = 0;
            while (total < buffer.length) {
                int count = line.read(buffer, total, buffer.length - total);
                if (count <= 0) {
                    return total;
                }
                total += count;
            }
            return total;
        }

        private void close() {
            capturing = false;
            ready = false;
            if (line != null) {
                line.stop();
                line.close();
            }
            if (inputDevice != null) {
                PlasmoVoiceSettings.closeInputDevice(inputDevice);
            }
            interrupt();
        }

        private boolean isReady() {
            InputDevice device = inputDevice;
            TargetDataLine currentLine = line;
            return ready && (device != null && device.isStarted() || currentLine != null && currentLine.isOpen());
        }
    }

    private record RemoteVoicePacket(
            UUID sender,
            byte[] encoded,
            long sequence,
            float distance,
            boolean positional,
            long receivedAt
    ) {
    }

    private static final class Playback implements AutoCloseable {

        private static final int QUEUE_LIMIT = 64;
        private static final long STARTUP_BUFFER_NANOS = 35_000_000L;
        private static final long CHANNEL_IDLE_NANOS = 30_000_000_000L;

        private final SimpleVoiceChatTransport transport;
        private final SourceDataLine line;
        private final LoopbackSource loopbackSource;
        private final AlContextOutputDevice outputDevice;
        private final Map<UUID, AudioChannel> channels = new ConcurrentHashMap<>();
        private final AtomicBoolean playing = new AtomicBoolean(true);
        private final Thread mixerThread;

        private Playback(SimpleVoiceChatTransport transport) throws DeviceException, LineUnavailableException {
            this.transport = transport;
            LoopbackSource source = null;
            AlContextOutputDevice selectedOutput = null;
            var voiceClient = PlasmoVoiceSettings.voiceClient();
            if (voiceClient != null) {
                try {
                    selectedOutput = PlasmoVoiceSettings.openOutputDevice(
                            new AudioFormat(SAMPLE_RATE, 16, 2, true, false)
                    );
                    source = voiceClient.getSourceManager().createLoopbackSource(true);
                    source.initialize(true);
                } catch (DeviceException | RuntimeException error) {
                    if (source != null) {
                        source.close();
                        source = null;
                    }
                    if (selectedOutput != null) {
                        PlasmoVoiceSettings.closeOutputDevice(selectedOutput);
                        selectedOutput = null;
                    }
                    BridgeLog.warn("Plasmo Voice output device is unavailable; using the system output instead");
                    BridgeLog.debug("Could not initialize Plasmo Voice playback for the SVC bridge", error);
                }
            }
            loopbackSource = source;
            outputDevice = selectedOutput;
            if (loopbackSource == null) {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format, FRAME_SAMPLES * 8);
                line.start();
            } else {
                line = null;
            }
            mixerThread = new Thread(this::mixLoop, "PV-SVC-Bridge-Audio");
            mixerThread.setDaemon(true);
            mixerThread.start();
        }

        private void offer(UUID channelId, RemoteVoicePacket packet) {
            long now = System.nanoTime();
            channels.compute(channelId, (ignored, channel) -> {
                if (channel == null && channels.size() >= MAX_AUDIO_CHANNELS) {
                    return null;
                }
                AudioChannel current = channel == null ? new AudioChannel() : channel;
                current.offer(packet, now);
                return current;
            });
        }

        private void mixLoop() {
            int[] left = new int[FRAME_SAMPLES];
            int[] right = new int[FRAME_SAMPLES];
            byte[] output = new byte[FRAME_SAMPLES * 4];
            short[] outputSamples = new short[FRAME_SAMPLES * 2];
            long nextFrameAt = System.nanoTime();
            try {
                while (playing.get()) {
                    Arrays.fill(left, (short) 0);
                    Arrays.fill(right, (short) 0);
                    long now = System.nanoTime();
                    SpatialSnapshot snapshot = transport.spatialSnapshot;
                    channels.forEach((id, channel) -> {
                        if (channel.isIdle(now)) {
                            channels.remove(id, channel);
                        }
                    });
                    for (AudioChannel channel : channels.values()) {
                        channel.mixInto(left, right, snapshot, now);
                    }
                    encodeStereo(left, right, output, outputSamples);
                    if (loopbackSource != null) {
                        loopbackSource.write(outputSamples);
                    } else {
                        int written = line.write(output, 0, output.length);
                        if (written != output.length && playing.get()) {
                            BridgeLog.warn("Audio output accepted only {} of {} bytes", written, output.length);
                        }
                    }
                    nextFrameAt += FRAME_DURATION_NANOS;
                    long remaining = nextFrameAt - System.nanoTime();
                    if (remaining > 0) {
                        LockSupport.parkNanos(remaining);
                    } else {
                        nextFrameAt = System.nanoTime();
                    }
                }
            } catch (RuntimeException error) {
                if (playing.get()) {
                    BridgeLog.error("Simple Voice Chat audio mixer stopped unexpectedly", error);
                }
            }
        }

        private static void encodeStereo(int[] left, int[] right, byte[] output, short[] outputSamples) {
            for (int i = 0; i < left.length; i++) {
                int leftSample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, left[i]));
                int rightSample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, right[i]));
                int offset = i * 4;
                output[offset] = (byte) leftSample;
                output[offset + 1] = (byte) (leftSample >>> 8);
                output[offset + 2] = (byte) rightSample;
                output[offset + 3] = (byte) (rightSample >>> 8);
                outputSamples[i * 2] = (short) leftSample;
                outputSamples[i * 2 + 1] = (short) rightSample;
            }
        }

        @Override
        public void close() {
            if (!playing.getAndSet(false)) {
                return;
            }
            mixerThread.interrupt();
            try {
                mixerThread.join(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (loopbackSource != null) {
                loopbackSource.close();
            }
            if (outputDevice != null) {
                PlasmoVoiceSettings.closeOutputDevice(outputDevice);
            }
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
            }
            channels.clear();
        }

        private static final class AudioChannel {

            private final TreeMap<Long, RemoteVoicePacket> pending = new TreeMap<>();
            private long expectedSequence;
            private boolean sequenceInitialized;
            private long lastActivity;
            private OpusDecoder decoder;
            private short[] decoded = new short[SAMPLE_RATE / 5];
            private int decodedLength;
            private int decodedOffset;
            private UUID sender;
            private float distance;
            private boolean positional;

            private synchronized void offer(RemoteVoicePacket packet, long now) {
                lastActivity = now;
                if (pending.size() >= QUEUE_LIMIT) {
                    pending.pollFirstEntry();
                }
                pending.putIfAbsent(packet.sequence(), packet);
            }

            private boolean isIdle(long now) {
                synchronized (this) {
                    return now - lastActivity > CHANNEL_IDLE_NANOS
                            && decodedOffset >= decodedLength
                            && pending.isEmpty();
                }
            }

            private void mixInto(int[] left, int[] right, SpatialSnapshot snapshot, long now) {
                int outputOffset = 0;
                while (outputOffset < FRAME_SAMPLES) {
                    if (decodedOffset >= decodedLength && !decodeNext(now)) {
                        return;
                    }
                    int count = Math.min(FRAME_SAMPLES - outputOffset, decodedLength - decodedOffset);
                    float attenuation = 1;
                    float pan = 0;
                    if (positional) {
                        SpeakerPosition position = snapshot.speakers().get(sender);
                        if (position == null) {
                            decodedOffset = decodedLength;
                            return;
                        }
                        double dx = position.x() - snapshot.listenerX();
                        double dy = position.y() - snapshot.listenerY();
                        double dz = position.z() - snapshot.listenerZ();
                        double distanceToListener = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (distance > 0) {
                            attenuation = (float) Math.max(0, 1 - Math.min(distanceToListener, distance) / distance);
                        }
                        if (distanceToListener > 0) {
                            double yaw = Math.toRadians(snapshot.listenerYaw());
                            double rightX = -Math.cos(yaw);
                            double rightZ = -Math.sin(yaw);
                            double verticalScale = Math.max(0, 1 - Math.abs(dy) / 32.0);
                            pan = (float) Math.max(-1, Math.min(1,
                                    ((dx * rightX + dz * rightZ) / distanceToListener) * verticalScale
                            ));
                        }
                    }
                    float playerVolume = (float) PlasmoVoiceSettings.playerVolume(sender);
                    if (PlasmoVoiceSettings.isPlayerMuted(sender)) {
                        playerVolume = 0;
                    }
                    float outputVolume = (float) PvSvcBridge.config().simpleVoiceChatVolume()
                            * (float) PlasmoVoiceSettings.playbackVolume();
                    if (PlasmoVoiceSettings.isPlaybackMuted()) {
                        outputVolume = 0;
                    }
                    float leftGain = attenuation * playerVolume * outputVolume
                            * (1 - Math.max(0, -pan) * 0.7f);
                    float rightGain = attenuation * playerVolume * outputVolume
                            * (1 - Math.max(0, pan) * 0.7f);
                    for (int i = 0; i < count; i++) {
                        int sample = decoded[decodedOffset + i];
                        int index = outputOffset + i;
                        left[index] += (int) (sample * leftGain);
                        right[index] += (int) (sample * rightGain);
                    }
                    decodedOffset += count;
                    outputOffset += count;
                }
            }

            private boolean decodeNext(long now) {
                RemoteVoicePacket packet = pollPacket(now);
                if (packet == null) {
                    decodedLength = 0;
                    decodedOffset = 0;
                    return false;
                }
                sender = packet.sender();
                distance = packet.distance();
                positional = packet.positional();
                if (packet.encoded().length == 0) {
                    decoder = null;
                    decodedLength = 0;
                    decodedOffset = 0;
                    return false;
                }
                try {
                    if (decoder == null) {
                        decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
                    }
                    decodedLength = decoder.decode(
                            packet.encoded(),
                            0,
                            packet.encoded().length,
                            decoded,
                            0,
                            decoded.length,
                            false
                    );
                    decodedOffset = 0;
                    return decodedLength > 0;
                } catch (OpusException error) {
                    decoder = null;
                    decodedLength = 0;
                    decodedOffset = 0;
                    BridgeLog.debug("Could not decode an incoming Simple Voice Chat audio packet", error);
                    return false;
                }
            }

            private synchronized RemoteVoicePacket pollPacket(long now) {
                if (pending.isEmpty()) {
                    return null;
                }
                if (!sequenceInitialized) {
                    RemoteVoicePacket first = pending.firstEntry().getValue();
                    if (pending.size() < 2 && now - first.receivedAt() < STARTUP_BUFFER_NANOS) {
                        return null;
                    }
                    expectedSequence = first.sequence();
                    sequenceInitialized = true;
                }
                RemoteVoicePacket packet = pending.remove(expectedSequence);
                if (packet != null) {
                    expectedSequence++;
                    return packet;
                }
                RemoteVoicePacket first = pending.firstEntry().getValue();
                if (now - first.receivedAt() < STARTUP_BUFFER_NANOS) {
                    return null;
                }
                expectedSequence = first.sequence() + 1;
                return firstEntryRemove();
            }

            private synchronized RemoteVoicePacket firstEntryRemove() {
                Map.Entry<Long, RemoteVoicePacket> first = pending.pollFirstEntry();
                return first == null ? null : first.getValue();
            }
        }
    }
}
