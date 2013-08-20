package jetbrains.buildServer.buildTriggers.vcs.vault.impl;

import com.intellij.util.containers.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jetbrains.buildServer.buildTriggers.vcs.vault.VaultConnection1;
import jetbrains.buildServer.buildTriggers.vcs.vault.VaultConnectionFactory;
import jetbrains.buildServer.buildTriggers.vcs.vault.VaultConnectionParameters;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Victory.Bedrosova on 8/19/13.
 */
public class VaultConnectionFactoryImpl implements VaultConnectionFactory {
  @NotNull
  private final Map<VaultConnectionParameters, VaultConnection1> myConnections = new HashMap<VaultConnectionParameters, VaultConnection1>();

  private static final ReentrantReadWriteLock CONNECTIONS_LOCK = new ReentrantReadWriteLock();

  @NotNull
  public VaultConnection1 getOrCreateConnection(@NotNull final VaultConnectionParameters parameters) {
    CONNECTIONS_LOCK.readLock().lock();
    try {
      if (myConnections.containsKey(parameters)) {
        return myConnections.get(parameters);
      }
    } finally {
      CONNECTIONS_LOCK.readLock().unlock();
    }

    CONNECTIONS_LOCK.writeLock().lock();
    try {
      if (myConnections.containsKey(parameters)) {
        return myConnections.get(parameters);
      }

      final VaultConnection1 connection1 = createConnection(parameters);
      myConnections.put(parameters, connection1);
      return connection1;

    } finally {
      CONNECTIONS_LOCK.writeLock().unlock();
    }
  }

  @NotNull
  private VaultConnection1 createConnection(@NotNull final VaultConnectionParameters parameters) {
    return makeSyncronized(makeEternal(new VaultConnection1Impl(parameters)));
  }

  @NotNull
  private SynchronizedVaultConnection makeSyncronized(@NotNull VaultConnection1 connection) {
    return new SynchronizedVaultConnection(connection);
  }

  @NotNull
  private EternalVaultConnection1 makeEternal(@NotNull VaultConnection1 connection) {
    return new EternalVaultConnection1(connection, this);
  }
}