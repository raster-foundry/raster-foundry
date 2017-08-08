/* globals BUILDCONFIG */

/* globals Auth0Lock heap */

import assetLogo from '../../../assets/images/logo-raster-foundry.png';
export default (app) => {
    class AuthService {
        constructor( // eslint-disable-line max-params
            jwtHelper, $q, $timeout, featureFlagOverrides, featureFlags,
            perUserFeatureFlags, $state, APP_CONFIG, localStorage,
            rollbarWrapperService, intercomService, $resource
        ) {
            this.localStorage = localStorage;
            this.jwtHelper = jwtHelper;
            this.$q = $q;
            this.$timeout = $timeout;
            this.$state = $state;
            this.featureFlags = featureFlags;
            this.perUserFeatureFlags = perUserFeatureFlags;
            this.featureFlagOverrides = featureFlagOverrides;
            this.intercomService = intercomService;
            this.rollbarWrapperService = rollbarWrapperService;

            if (!APP_CONFIG.error) {
                this.initAuth0(APP_CONFIG);
            }

            this.pendingReauth = null;

            this.User = $resource(`${BUILDCONFIG.API_HOST}/api/users/:id`, {
                id: '@id'
            }, {
                query: {
                    method: 'GET',
                    cache: false
                },
                get: {
                    method: 'GET',
                    cache: false
                }
            });
        }

        initAuth0(APP_CONFIG) {
            let loginOptions = {
                closable: false,
                auth: {
                    redirect: false,
                    sso: true
                },
                theme: {
                    logo: assetLogo,
                    primaryColor: '#5e509b'
                },
                additionalSignUpFields: [{
                    name: 'companyName',
                    placeholder: 'Company name'
                }, {
                    name: 'companySize',
                    placeholder: 'How large is your company?'
                }, {
                    name: 'reference',
                    placeholder: 'How\'d you find out about us?'
                }, {
                    name: 'phoneNumber',
                    placeholder: 'Phone Number'
                }]
            };

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

            this.loginLock = new Auth0Lock(
                APP_CONFIG.clientId, APP_CONFIG.auth0Domain, loginOptions
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

            this.loginLock.on('authenticated', this.onLogin.bind(this));
            this.loginLock.on('authorization_error', this.onLoginFail.bind(this));
        }

        login(token) {
            try {
                if (!token) {
                    this.loginLock.show();
                } else if (!this.jwtHelper.isTokenExpired(token)) {
                    this.onLogin({idToken: token});
                } else if (this.jwtHelper.isTokenExpired(token)) {
                    this.localStorage.remove('item_token');
                    this.$state.go('login');
                } else {
                    this.loginLock.show();
                }
            } catch (e) {
                this.loginLock.show();
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

        getCurrentUser() {
            let id = this.profile().user_id;
            return this.User.get({id: id}).$promise;
        }

        onLogin(authResult) {
            this.localStorage.set('id_token', authResult.idToken);

            this.setReauthentication(authResult.idToken);

            this.loginLock.getProfile(authResult.idToken, (error, profile) => {
                if (error) {
                    return;
                }

                this.localStorage.set('profile', profile);

                if (typeof heap !== 'undefined' &&
                    typeof heap.identify === 'function' &&
                    typeof heap.addUserProperties === 'function' &&
                    typeof heap.addEventProperties === 'function'
                   ) {
                    heap.identify(profile.email);
                    this.getCurrentUser().then((user) => {
                        heap.addUserProperties({
                            'organization': user.organizationId,
                            'impersonated': profile.impersonated || false,
                            'impersonator': profile.impersonated ? profile.impersonator.email : null
                        });
                        heap.addEventProperties({'Logged In': 'true'});
                    });
                }

                this.featureFlagOverrides.setUser(profile.user_id);
                // Flags set in the `/config` endpoint; default.
                let configFlags = this.featureFlags.get().map((flag) => flag.key);
                // Now that we've authenticated, trigger an override of the default
                // feature flags from `/conf` with per-user flags from `/feature-flags
                this.featureFlags.set(this.perUserFeatureFlags.load()).then(() => {
                    // Override API-specified feature flags with flags from Auth0 metadata
                    // TODO: We may want to remove this feature and provide all per-user
                    // feature flags from the `/feature-flags` endpoint, but at the moment
                    // the only per-user flags that the endpoint supports are based on the
                    // user's organization, so we need to keep this around to provide more
                    // granular control over per-user feature flags.
                    let userFlags = profile.user_metadata && profile.user_metadata.featureFlags ?
                        profile.user_metadata.featureFlags : [];
                    let flagOverrides = userFlags.filter((flag) => {
                        return configFlags.includes(flag.key);
                    });
                    this.featureFlags.set(flagOverrides);
                });
                this.rollbarWrapperService.init(profile);
                this.isLoggedIn = true;
                this.loginLock.hide();
                if (authResult.refreshToken) {
                    this.promise.resolve(authResult);
                    delete this.promise;
                }
                this.$state.go('home');
                this.intercomService.bootWithUser(profile);
            });
        }

        onImpersonation(hash) {
            const rawParams = hash.split('&');
            rawParams.forEach(p => {
                const [k, v] = p.split('=');
                if (k === 'id_token') {
                    this.onLogin({ idToken: v});
                }
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
            if (typeof heap !== 'undefined' && typeof heap.removeEventProperty === 'function') {
                heap.removeEventProperty('Logged In');
            }
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
            try {
                const token = this.token();
                this.isLoggedIn = Boolean(
                    token && this.profile() && !this.jwtHelper.isTokenExpired(token)
                );
                if (this.isLoggedIn) {
                    this.setReauthentication(token);
                }
                return this.isLoggedIn;
            } catch (e) {
                this.$state.go('login');
                return false;
            }
        }

        setReauthentication(token) {
            if (!this.pendingReauth) {
                // Set up a $timeout to reauthenticate 5 minutes before the token will expire
                try {
                    const expDate = this.jwtHelper.getTokenExpirationDate(token);
                    const nowDate = new Date();
                    const timeoutMillis = expDate.getTime() - nowDate.getTime() - 5 * 60 * 1000;
                    // Store in case we need to cancel for some reason
                    this.pendingReauth = this.$timeout(() => this.login(null), timeoutMillis);
                } catch (e) {
                    this.$state.go('login');
                }
            }
        }

    }

    app.service('authService', AuthService);
};
