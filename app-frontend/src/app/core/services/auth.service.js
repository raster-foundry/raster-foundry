/* globals Auth0Lock */

import assetLogo from '../../../assets/images/logo-raster-foundry.png';
export default (app) => {
    class AuthService {
        constructor( // eslint-disable-line max-params
            lock, jwtHelper, $q, featureFlagOverrides, featureFlags,
            $state, APP_CONFIG, localStorage, rollbarWrapperService, intercomService
        ) {
            this.lock = lock;
            this.localStorage = localStorage;
            this.jwtHelper = jwtHelper;
            this.$q = $q;
            this.$state = $state;
            this.featureFlags = featureFlags;
            this.featureFlagOverrides = featureFlagOverrides;
            this.intercomService = intercomService;
            this.rollbarWrapperService = rollbarWrapperService;

            let passResetOptions = {
                initialScreen: 'forgotPassword',
                allowLogin: false,
                closable: true,
                prefill: {
                    email: this.profile() && this.profile().email
                },
                auth: {
                    redirect: false,
                    sso: true
                },
                theme: {
                    logo: assetLogo,
                    primaryColor: '#5e509b'
                }
            };
            this.resetLock = new Auth0Lock(
                APP_CONFIG.clientId, APP_CONFIG.auth0Domain, passResetOptions
            );

            let tokenCreateOptions = {
                initialScreen: 'login',
                allowLogin: true,
                allowSignUp: false,
                allowForgotPassword: false,
                autoclose: true,
                rememberLastLogin: false,
                closable: true,
                auth: {
                    redirect: false,
                    sso: true
                },
                theme: {
                    logo: assetLogo,
                    primaryColor: '#5e509b'
                }
            };

            this.tokenCreateLock = new Auth0Lock(
                APP_CONFIG.clientId, APP_CONFIG.auth0Domain, tokenCreateOptions
            );

            this.tokenCreateLock.on('authenticated', this.onTokenCreated.bind(this));
            this.tokenCreateLock.on('authorization_error', this.onTokenCreateFail.bind(this));

            lock.on('authenticated', this.onLogin.bind(this));
            lock.on('authorization_error', this.onLoginFail.bind(this));
        }

        login(token) {
            if (!token) {
                this.lock.show();
            } else if (!this.jwtHelper.isTokenExpired(token)) {
                this.onLogin({idToken: token});
            } else if (this.jwtHelper.isTokenExpired(token)) {
                this.localStorage.remove('item_token');
                this.$state.go('login');
            } else {
                this.lock.show();
            }
        }

        onTokenCreated(authResult) {
            if (this.promise) {
                this.promise.resolve(authResult);
                delete this.promise;
            }
        }

        onTokenCreateFail(error) {
            if (this.promise) {
                this.promise.reject(error);
                delete this.promise;
            }
        }

        changePassword() {
            this.resetLock.show();
        }

        onLogin(authResult) {
            this.localStorage.set('id_token', authResult.idToken);

            this.lock.getProfile(authResult.idToken, (error, profile) => {
                if (error) {
                    return;
                }

                /* eslint-disable no-undef */
                if (typeof heap !== 'undefined' && typeof heap.identify === 'function') {
                    heap.identify(profile.email);
                }
                /* eslint-enable no-undef */

                this.localStorage.set('profile', profile);
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
                this.rollbarWrapperService.init(profile);
                this.isLoggedIn = true;
                this.lock.hide();
                if (authResult.refreshToken) {
                    this.promise.resolve(authResult);
                    delete this.promise;
                }
                this.$state.go('home');
                this.intercomService.bootWithUser(profile);
            });
        }

        onLoginFail(error) {
            if (this.promise) {
                this.promise.reject(error);
                delete this.promise;
            }
        }

        profile() {
            return this.localStorage.get('profile');
        }

        token() {
            return this.localStorage.get('id_token', this.token);
        }

        logout() {
            this.localStorage.remove('id_token');
            this.localStorage.remove('profile');
            this.rollbarWrapperService.init();
            this.isLoggedIn = false;
            this.intercomService.shutdown();
            this.$state.go('login');
        }

        createRefreshToken(name) {
            this.promise = this.$q.defer();
            this.lastTokenName = name;
            this.tokenCreateLock.show({
                auth: {
                    params: {
                        scope: 'openid offline_access',
                        device: name
                    }
                }
            });
            return this.promise.promise;
        }

        verifyAuthCache() {
            this.isLoggedIn = Boolean(
                this.localStorage.get('id_token') && this.localStorage.get('profile')
            );
            return this.isLoggedIn;
        }
    }

    app.service('authService', AuthService);
};
