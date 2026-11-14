package com.corz.client.utils.prestige;

import ai.auth.ModuleRegistry;
import com.corz.client.categories.movement.Freecam;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.util.math.Vec3d;

public class SilentRotationManager implements MinecraftAccess {
    private final CopyOnWriteArrayList<RotationProvider> rotationProviders;
    private Rotation activeRotation;
    private Rotation serverRotation;
    private Rotation lastSentRotation;
    private static float yawWrapStep = 300.0F;
    private static float maxRotationStep = 90.0F;
    private Rotation resyncRotation;
    boolean actedThisTick;
    private Object lockOwner;
    private Rotation lockedRotation;
    private Object lockWorld;
    final List<Runnable> scheduledTasks;

    public void tick() {
        this.actedThisTick = false;
        if (this.lockWorld != null && this.lockWorld != mc.world) {
            this.clearLock();
        }
        if (!this.scheduledTasks.isEmpty()) {
            ArrayList<Runnable> tasks = new ArrayList<>(this.scheduledTasks);
            this.scheduledTasks.clear();

            for (Runnable task : tasks) {
                try {
                    task.run();
                } catch (Throwable ignored) {
                }
            }
        }

        if (mc.player == null) {
            this.serverRotation = null;
            this.lastSentRotation = null;
        } else if (this.rotationProviders.isEmpty() && this.activeRotation == null && this.resyncRotation == null) {
            this.serverRotation = null;
            this.lastSentRotation = null;
        } else {
            if (this.serverRotation == null) {
                this.serverRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());
            }

            boolean requested = false;
            boolean hadActiveRotation = this.activeRotation != null;

            for (RotationProvider provider : this.rotationProviders) {
                Rotation requestedRotation = provider.provideRotation();
                if (requestedRotation != null) {
                    this.activeRotation = requestedRotation;
                    requested = true;
                }
            }

            if (!requested && this.activeRotation != null) {
                if (Math.abs(this.activeRotation.getYaw() - mc.player.getYaw()) > yawWrapStep) {
                    this.activeRotation = new Rotation(this.activeRotation.getYaw() - yawWrapStep * Math.signum(this.activeRotation.getYaw() - mc.player.getYaw()), this.activeRotation.getPitch());
                } else {
                    this.activeRotation = null;
                }
            }

            if (hadActiveRotation && this.activeRotation == null) {
                this.resyncRotation = new Rotation(mc.player.getYaw() + (float)(Math.random() * 0.06 - 0.03), mc.player.getPitch() + (float)(Math.random() * 0.06 - 0.03));
            } else if (this.activeRotation != null) {
                this.resyncRotation = null;
            }

            this.serverRotation = this.activeRotation != null ? this.activeRotation : (this.resyncRotation != null ? this.resyncRotation : null);
        }
    }

    public Rotation getRotationForPacket() {
        if (this.activeRotation != null) {
            return this.activeRotation;
        } else if (this.resyncRotation != null) {
            Rotation rotation = this.resyncRotation;
            this.resyncRotation = null;
            return rotation;
        } else {
            return null;
        }
    }

    public void onMovementPacketSent() {
        this.lastSentRotation = this.activeRotation;
    }

    public void setActiveRotation(Rotation rotation) {
        this.activeRotation = rotation;
    }

    public void clearRotation() {
        this.activeRotation = null;
        this.resyncRotation = null;
    }

    public Rotation getRotationTowards(Vec3d target) {
        return RotationUtil.getLimitedRotationTo(this.serverRotation != null ? this.serverRotation : new Rotation(mc.player.getYaw(), mc.player.getPitch()), target, maxRotationStep);
    }

    public Rotation getServerRotation() {
        return this.serverRotation;
    }

    public Rotation getActiveRotation() {
        return this.activeRotation;
    }

    public synchronized boolean tryLockRotation(Object owner, Rotation rotation) {
        if (owner != null && rotation != null) {
            if (!this.canRotate()) {
                this.clearLock();
                return false;
            } else {
                if (this.lockWorld != mc.world) {
                    this.clearLock();
                }

                if (this.lockOwner != null && this.lockOwner != owner) {
                    return false;
                } else {
                    this.lockOwner = owner;
                    this.lockedRotation = new Rotation(rotation.getYaw(), rotation.getPitch());
                    this.lockWorld = mc.world;
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    public synchronized boolean isLockHeldBy(Object owner) {
        if (owner != null && this.lockedRotation != null) {
            if (this.lockWorld != mc.world || !this.canRotate()) {
                this.clearLock();
            }

            return this.lockOwner == owner && this.lockedRotation != null;
        } else {
            return false;
        }
    }

    public synchronized Rotation getLockedRotation() {
        if (this.lockedRotation == null) {
            return null;
        } else {
            if (this.lockWorld != mc.world || !this.canRotate()) {
                this.clearLock();
            }

            return this.lockedRotation == null ? null : new Rotation(this.lockedRotation.getYaw(), this.lockedRotation.getPitch());
        }
    }

    public synchronized void releaseLock(Object owner) {
        if (owner != null && this.lockOwner == owner) {
            this.clearLock();
        }
    }

    public boolean canRotate() {
        if (mc.player != null && mc.world != null && !mc.player.hasVehicle() && mc.getCameraEntity() == mc.player) {
            Freecam freecam = (Freecam)ModuleRegistry.getModule(Freecam.class);
            return freecam == null || !freecam.isEnabled();
        } else {
            return false;
        }
    }

    private void clearLock() {
        this.lockOwner = null;
        this.lockedRotation = null;
        this.lockWorld = null;
    }

    public SilentRotationManager() {
        super();
        this.rotationProviders = new CopyOnWriteArrayList<>();
        this.resyncRotation = null;
        this.scheduledTasks = new CopyOnWriteArrayList<>();
    }

    public void addRotationProvider(RotationProvider provider) {
        if (this.rotationProviders.addIfAbsent(provider)) {
            this.rotationProviders.sort(Comparator.comparingInt(RotationProvider::getRotationPriority).reversed());
        }
    }

    public void removeRotationProvider(RotationProvider provider) {
        this.rotationProviders.remove(provider);
    }

    public boolean hasActedThisTick() {
        return this.actedThisTick;
    }

    public void setActedThisTick(boolean acted) {
        this.actedThisTick = acted;
    }

    public void scheduleTask(Runnable task) {
        if (task != null) {
            this.scheduledTasks.add(task);
        }
    }
}
