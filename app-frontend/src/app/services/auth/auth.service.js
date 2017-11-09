/* globals BUILDCONFIG */

/* globals Auth0Lock heap */

import assetLogo from '../../../assets/images/logo-raster-foundry.png';
export default (app) => {
    class AuthService {
        constructor( // eslint-disable-line max-params
            jwtHelper, $q, $timeout, featureFlagOverrides, featureFlags,
            perUserFeatureFlags, $state, APP_CONFIG, localStorage,
            rollbarWrapperService, intercomService, $resource, $location
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
            this.APP_CONFIG = APP_CONFIG;
            this.$location = $location;

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
                oidcConformant: true,
                auth: {
                    autoParseHash: false,
                    redirect: true,
                    sso: true,
                    responseType: 'token id_token',
                    redirectUrl: `${this.getBaseURL()}/login`,
                    params: {
                        scope: 'openid profile email',
                        audience: `https://${this.APP_CONFIG.auth0Domain}/api/v2/`
                    }
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
                oidcConformant: true,
                prefill: {
                    email: this.getProfile() && this.getProfile().email
                },
                auth: {
                    redirect: true,
                    redirectUrl: `${this.getBaseURL()}/login`,
                    sso: true,
                    params: {
                        audience: `https://${this.APP_CONFIG.auth0Domain}/api/v2/`
                    }
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
                oidcConformant: true,
                initialScreen: 'login',
                allowLogin: true,
                allowSignUp: false,
                allowForgotPassword: false,
                autoclose: true,
                rememberLastLogin: false,
                closable: true,
                auth: {
                    // eslint-disable-next-line
                    grant_type: 'authorization_code',
                    redirect: true,
                    sso: true,
                    redirectUrl: `${this.getBaseURL()}/settings/tokens/api`,
                    params: {
                        scope: 'openid offline_access',
                        responseType: 'code',
                        audience: `https://${this.APP_CONFIG.auth0Domain}/api/v2/`
                    }
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

            this.tokenCreateLock = new Auth0Lock(
                APP_CONFIG.clientId, APP_CONFIG.auth0Domain, tokenCreateOptions
            );

            this.tokenCreateLock.on('authenticated', this.onTokenCreated.bind(this));
            this.tokenCreateLock.on('authorization_error', this.onTokenCreateFail.bind(this));

            this.loginLock.on('authenticated', this.onLogin.bind(this));
            this.loginLock.on('authorization_error', this.onLoginFail.bind(this));
        }

        getBaseURL() {
            let host = BUILDCONFIG.API_HOST || this.$location.host();
            let protocol = this.$location.protocol();
            let port = this.$location.port();
            let formattedPort = port !== 80 && port !== 443 ? ':' + port : '';
            return `${protocol}://${host}${formattedPort}`;
        }

        login(accessToken, idToken) {
            try {
                if (!accessToken) {
                    this.loginLock.show();
                } else if (!this.jwtHelper.isTokenExpired(accessToken)) {
                    this.onLogin({accessToken, idToken});
                } else if (this.jwtHelper.isTokenExpired(accessToken)) {
                    this.localStorage.remove('accessToken');
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
            let id = this.getProfile().sub;
            return this.User.get({id: id}).$promise;
        }

        onLogin(authResult) {
            this.localStorage.set('accessToken', authResult.accessToken);
            this.localStorage.set('idToken', authResult.idToken);

            this.setReauthentication(authResult.accessToken);

            this.profile = this.jwtHelper.decodeToken(authResult.idToken);

            if (typeof heap !== 'undefined' &&
                typeof heap.identify === 'function' &&
                typeof heap.addUserProperties === 'function' &&
                typeof heap.addEventProperties === 'function'
               ) {
                heap.identify(this.profile.email);
                this.getCurrentUser().then((user) => {
                    heap.addUserProperties({
                        'organization': user.organizationId,
                        'impersonated': this.profile.impersonated || false,
                        'impersonator': this.profile.impersonated ?
                            this.profile.impersonator.email : null
                    });
                    heap.addEventProperties({'Logged In': 'true'});
                });
            }

            this.featureFlagOverrides.setUser(this.profile);
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
                let userFlags = this.profile.user_metadata &&
                    this.profile.user_metadata.featureFlags ?
                    this.profile.user_metadata.featureFlags : [];
                let flagOverrides = userFlags.filter((flag) => {
                    return configFlags.includes(flag.key);
                });
                this.featureFlags.set(flagOverrides);
            });
            this.rollbarWrapperService.init(this.profile);
            this.isLoggedIn = true;
            this.loginLock.hide();
            if (authResult.refreshToken) {
                this.promise.resolve(authResult);
                delete this.promise;
            }
            this.$state.go('home');
            this.intercomService.bootWithUser(this.profile);
        }

        extractRedirectParams(hash) {
            const rawParams = hash.split('&');
            let params = {};
            rawParams.forEach(p => {
                const [k, v] = p.split('=');
                params[k] = v;
            });
            return params;
        }

        onRedirectComplete(hash) {
            let params = this.extractRedirectParams(hash);
            if (params.access_token) {
                this.onLogin({ accessToken: params.access_token, idToken: params.id_token });
            }
        }

        onLoginFail(error) {
            if (this.promise) {
                this.promise.reject(error);
                delete this.promise;
            }
        }

        getProfile() {
            if (!this.profile) {
                let idToken = this.localStorage.get('idToken');
                this.profile = idToken && this.jwtHelper.decodeToken(idToken);
            }
            return this.profile;
        }

        token() {
            if (!this.accessToken) {
                this.accessToken = this.localStorage.get('accessToken');
            }
            return this.accessToken;
        }

        logout() {
            if (typeof heap !== 'undefined' && typeof heap.removeEventProperty === 'function') {
                heap.removeEventProperty('Logged In');
            }
            delete this.accessToken;
            delete this.profile;
            this.localStorage.remove('idToken');
            this.localStorage.remove('accessToken');
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
                    token && this.getProfile() && !this.jwtHelper.isTokenExpired(token)
                );
                if (this.isLoggedIn) {
                    this.setReauthentication(token);
                    this.featureFlagOverrides.setUser(this.getProfile());
                }
                return this.isLoggedIn;
            } catch (e) {
                this.$state.go('login');
                return false;
            }
        }

        setReauthentication(accessToken) {
            if (!this.pendingReauth) {
                // Set up a $timeout to reauthenticate 5 minutes before the token will expire
                try {
                    const expDate = this.jwtHelper.getTokenExpirationDate(accessToken);
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
