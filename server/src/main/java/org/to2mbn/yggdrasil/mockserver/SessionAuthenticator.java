package org.to2mbn.yggdrasil.mockserver;

import static java.util.Optional.empty;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.to2mbn.yggdrasil.mockserver.TokenStore.Token;
import org.to2mbn.yggdrasil.mockserver.YggdrasilDatabase.YggdrasilCharacter;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

@Component
@ConfigurationProperties(prefix = "yggdrasil.session", ignoreUnknownFields = false)
public class SessionAuthenticator {

	private static final int MAX_AUTH_COUNT = 100_000;

	public static class PendingAuthentication {
		public String serverId;

		public Token token;

		@Nullable
		public String ip;

		public long createdAt;
	}

	private ConcurrentLinkedHashMap<String, PendingAuthentication> serverId2auth = new ConcurrentLinkedHashMap.Builder<String, PendingAuthentication>()
			.maximumWeightedCapacity(MAX_AUTH_COUNT)
			.build();

	private Duration authExpireTime;

	/**
	 * @param token
	 *            the token is assumed to be valid
	 */
	public void joinServer(Token token, String serverId, @Nullable String ip) {
		PendingAuthentication auth = new PendingAuthentication();
		auth.token = token;
		auth.serverId = serverId;
		auth.ip = ip;
		auth.createdAt = System.currentTimeMillis();
		serverId2auth.put(serverId, auth);
	}

	public Optional<YggdrasilCharacter> verifyUser(String username, String serverId, @Nullable String ip) {
		PendingAuthentication auth = serverId2auth.remove(serverId);
		if (auth == null
				|| System.currentTimeMillis() > auth.createdAt + authExpireTime.toMillis()
				|| !auth.token.getBoundCharacter().isPresent()
				|| !auth.token.getBoundCharacter().get().getName().equals(username)
				|| (ip != null && !ip.equals(auth.ip)))
			return empty();

		return auth.token.getBoundCharacter();
	}

	public int pendingAuthenticationsCount() {
		return serverId2auth.size();
	}

	public Duration getAuthExpireTime() {
		return authExpireTime;
	}

	public void setAuthExpireTime(Duration authExpireTime) {
		this.authExpireTime = authExpireTime;
	}
}
