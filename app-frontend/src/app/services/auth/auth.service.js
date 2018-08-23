/* globals BUILDCONFIG Auth0Lock heap $ window Uint8Array*/
import ApiActions from '_redux/actions/api-actions';

let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

export default (app) => {
    class AuthService {
        constructor( // eslint-disable-line max-params
            jwtHelper, $q, $timeout, featureFlagOverrides, featureFlags,
            perUserFeatureFlags, $state, APP_CONFIG, localStorage,
            rollbarWrapperService, intercomService, $resource, $ngRedux, $location

        ) {
            this.localStorage = localStorage;
            this.jwtHelper = jwtHelper;
            this.$q = $q;

            this.$timeout = $timeout;
            this.$state = $state;
            this.userRoles = [];
            this.featureFlags = featureFlags;
            this.perUserFeatureFlags = perUserFeatureFlags;
            this.featureFlagOverrides = featureFlagOverrides;
            this.intercomService = intercomService;
            this.rollbarWrapperService = rollbarWrapperService;
            this.APP_CONFIG = APP_CONFIG;
            this.redirectConnection = BUILDCONFIG.REDIRECT_CONNECTION;
            this._redux = {};
            $ngRedux.connect((state) => {
                return {
                    apiToken: state.api.apiToken,
                    apiUrl: state.api.apiUrl,
                    tileUrl: state.api.tileUrl
                };
            }, ApiActions)(this._redux);
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
                },
                roles: {
                    method: 'GET',
                    cache: false,
                    url: `${BUILDCONFIG.API_HOST}/api/users/me/roles`,
                    isArray: true
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
                    primaryColor: BUILDCONFIG.AUTH0_PRIMARY_COLOR
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
                }],
                allowedConnections: ['Username-Password-Authentication', 'google-oauth2']
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
                    primaryColor: BUILDCONFIG.AUTH0_PRIMARY_COLOR
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
                    redirectUrl: `${this.getBaseURL()}/user/me/settings/api-tokens`,
                    params: {
                        scope: 'openid offline_access',
                        responseType: 'code',
                        audience: `https://${this.APP_CONFIG.auth0Domain}/api/v2/`
                    }
                },
                theme: {
                    logo: assetLogo,
                    primaryColor: BUILDCONFIG.AUTH0_PRIMARY_COLOR
                },
                allowedConnections: ['Username-Password-Authentication', 'google-oauth2']
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
            let host = this.$location.host();
            let protocol = this.$location.protocol();
            let port = this.$location.port();
            let formattedPort = port !== 80 && port !== 443 ? ':' + port : '';
            return `${protocol}://${host}${formattedPort}`;
        }

        redirectToConnection() {
            const clientId = this.redirectConnection.clientId;
            const connection = this.redirectConnection.connection;

            const nonce = this.randomString(35);
            this.localStorage.set('nonce', nonce);
            // eslint-disable-next-line
            const tentantUrl = `https://${this.APP_CONFIG.auth0Domain}/authorize/?client_id=${clientId}&response_type=token id_token&scope=openid profile email&redirect_uri=${this.getBaseURL()}/login&connection=${connection}&nonce=${nonce}`;
            window.location = tentantUrl;
        }

        createConnectionToken(name) {
            const clientId = this.redirectConnection.clientId;
            const connection = this.redirectConnection.connection;

            const nonce = this.randomString(35);
            this.localStorage.set('nonce', nonce);
            // eslint-disable-next-line
            const tentantUrl = `https://${this.APP_CONFIG.auth0Domain}/authorize/?client_id=${clientId}&response_type=code&scope=openid offline_access&redirect_uri=${this.getBaseURL()}/user/me/settings/api-tokens&connection=${connection}&nonce=${nonce}&audience=https://${this.APP_CONFIG.auth0Domain}/api/v2/&device=${name}`;
            window.location = tentantUrl;
        }

        login(accessToken, idToken) {
            try {
                if (!accessToken) {
                    if (this.redirectConnection) {
                        this.redirectToConnection();
                    } else {
                        this.loginLock.show();
                    }
                } else if (!this.jwtHelper.isTokenExpired(accessToken)) {
                    this.onLogin({accessToken, idToken});
                    this.localStorage.remove('authUrlRestore');
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
            let promise = this.User.get({id: id}).$promise;
            promise.then((user) => {
                this.user = user;
            });
            return promise;
        }

        getUserRoles() {
            return this.userRoles;
        }

        onLogin(authResult) {
            this.localStorage.set('accessToken', authResult.accessToken);
            this.localStorage.set('idToken', authResult.idToken);
            this.profile = this.jwtHelper.decodeToken(authResult.idToken);

            if (typeof heap !== 'undefined' && typeof heap.identify === 'function') {
                heap.identify(this.profile.email);
            }

            this.getCurrentUser().then(user => {
                this._redux.initApi({
                    apiToken: authResult.idToken,
                    apiUrl: BUILDCONFIG.API_HOST,
                    tileUrl: this.APP_CONFIG.tileServerLocation,
                    user
                });
                if (typeof heap !== 'undefined' &&
                    typeof heap.identify === 'function' &&
                    typeof heap.addUserProperties === 'function' &&
                    typeof heap.addEventProperties === 'function'
                   ) {
                    heap.identify(this.profile.email);
                    heap.addUserProperties({
                        'organization': user.organizationId,
                        'impersonated': this.profile.impersonated || false,
                        'impersonator': this.profile.impersonated ?
                            this.profile.impersonator.email : null
                    });
                    heap.addEventProperties({'Logged In': 'true'});
                }
            });

            this.fetchUserRoles().then((response) => {
                this.userRoles = response;
            });

            this.setReauthentication(authResult.idToken);

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
            // Doesn't store url parameters, but they matter less
            const restore = this.localStorage.get('authUrlRestore');
            if (restore) {
                this.$location.path(restore.path).search(restore.search);
                this.localStorage.remove('authUrlRestore');
            } else {
                this.$state.go('home');
            }
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
                let idToken = this.token();
                this.profile = idToken && this.jwtHelper.decodeToken(idToken);
            }
            return this.profile;
        }

        token(force = false) {
            if (!this.idToken || force) {
                this.idToken = this.localStorage.get('idToken');
            }
            return this.idToken;
        }

        logout() {
            if (typeof heap !== 'undefined' && typeof heap.removeEventProperty === 'function') {
                heap.removeEventProperty('Logged In');
            }
            delete this.accessToken;
            delete this.idToken;
            delete this.profile;
            this.localStorage.remove('idToken');
            this.localStorage.remove('accessToken');
            this.rollbarWrapperService.init();
            this.isLoggedIn = false;
            this.intercomService.shutdown();
            this.$state.go('login');
        }

        clearAuthStorage() {
            delete this.accessToken;
            delete this.idToken;
            this.localStorage.remove('idToken');
            this.localStorage.remove('accessToken');
        }

        createRefreshToken(name) {
            this.promise = this.$q.defer();
            this.lastTokenName = name;

            if (this.redirectConnection) {
                this.createConnectionToken(name);
            } else {
                this.tokenCreateLock.show({
                    auth: {
                        params: {
                            scope: 'openid offline_access',
                            device: name
                        }
                    }
                });
            }
            return this.promise.promise;
        }

        verifyAuthCache() {
            try {
                const token = this.token(true);
                this.isLoggedIn = Boolean(
                    token && this.getProfile() && !this.jwtHelper.isTokenExpired(token)
                );
                if (this.isLoggedIn) {
                    if (!this._redux.apiToken) {
                        this.getCurrentUser().then(user => {
                            this._redux.initApi({
                                apiToken: this.token(),
                                apiUrl: BUILDCONFIG.API_HOST,
                                tileUrl: this.APP_CONFIG.tileServerLocation,
                                user: user
                            });
                        });

                        this.fetchUserRoles().then((response) => {
                            this.userRoles = response;
                        });
                    }

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
                    const timeoutMillis = expDate.getTime() - nowDate.getTime();
                    // Store in case we need to cancel for some reason
                    this.pendingReauth = this.$timeout(() => {
                        this.logout();
                    }, timeoutMillis);
                } catch (e) {
                    this.$state.go('login');
                }
            }
        }

        randomString(length) {
            const bytes = new Uint8Array(length);
            const random = window.crypto.getRandomValues(bytes);
            const result = [];
            const charset = '0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._~';
            random.forEach(function (c) {
                result.push(charset[c % charset.length]);
            });
            return result.join('');
        }

        fetchUserRoles() {
            return this.User.roles().$promise;
        }

        isEffectiveAdmin(groupIds) {
            const ids = [].concat(groupIds || []);
            return this.user &&
                this.user.isSuperuser ||
                !!this.userRoles.find(r => {
                    return r.groupRole === 'ADMIN' &&
                        ids.includes(r.groupId) &&
                        r.membershipStatus === 'APPROVED';
                });
        }
    }

    app.service('authService', AuthService);
};
