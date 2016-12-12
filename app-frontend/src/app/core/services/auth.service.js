export default (app) => {
    class AuthService {
        constructor( // eslint-disable-line max-params
            lock, store, jwtHelper, $q, featureFlagOverrides, featureFlags
        ) {
            this.lock = lock;
            this.store = store;
            this.jwtHelper = jwtHelper;
            this.$q = $q;
            this.featureFlags = featureFlags;
            this.featureFlagOverrides = featureFlagOverrides;

            lock.on('authenticated', this.onLogin.bind(this));
            lock.on('authorization_error', this.onLoginFail.bind(this));
        }

        login(token) {
            if (!token) {
                this.lock.show();
            } else if (!this.jwtHelper.isTokenExpired(token)) {
                this.onLogin({idToken: token});
            } else {
                this.store.remove('item_token');
                this.lock.show();
            }
        }

        onLogin(authResult) {
            this.store.set('id_token', authResult.idToken);

            this.lock.getProfile(authResult.idToken, (error, profile) => {
                if (error) {
                    return;
                }

                this.store.set('profile', profile);
                this.featureFlagOverrides.setUser(profile.user_id);
                let userFlags = profile.user_metadata && profile.user_metadata.featureFlags ?
                    profile.user_metadata.featureFlags : [];
                // Not using a set because performance considerations are negligible,
                // and it would require an additional import
                let configFlags = this.featureFlags.get().map((flag) => flag.key);
                let flagOverrides = userFlags.filter((flag) => {
                    return configFlags.includes(flag.key);
                });
                this.featureFlags.set(flagOverrides);
            });
            this.isLoggedIn = true;
            this.lock.hide();
            if (authResult.refreshToken) {
                this.promise.resolve(authResult);
                delete this.promise;
            }
        }

        onLoginFail(error) {
            if (this.promise) {
                this.promise.reject(error);
                delete this.promise;
            }
        }

        profile() {
            return this.store.get('profile');
        }

        token() {
            return this.store.get('id_token', this.token);
        }

        logout() {
            this.store.remove('id_token');
            this.store.remove('profile');
            this.isLoggedIn = false;
            this.login();
        }

        createRefreshToken(name) {
            this.promise = this.$q.defer();
            this.lastTokenName = name;
            this.lock.show({
                allowSignUp: false,
                allowForgotPassword: false,
                rememberLastLogin: false,
                auth: {
                    params: {
                        scope: 'openid offline_access',
                        device: name
                    }
                }
            });
            return this.promise.promise;
        }
    }

    app.service('authService', AuthService);
};
