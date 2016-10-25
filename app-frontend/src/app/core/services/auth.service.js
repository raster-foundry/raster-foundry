export default (app) => {
    class AuthService {
        constructor(lock, store, jwtHelper) {
            this.lock = lock;
            this.store = store;
            this.jwtHelper = jwtHelper;

            lock.on('authenticated', this.onLogin.bind(this));
        }

        login(token) {
            if (!token) {
                this.lock.show();
            } else if (!this.jwtHelper.isTokenExpired(token)) {
                this.onLogin({idToken: token});
            } else {
                this.store.remove('item_token');
            }
        }

        onLogin(authResult) {
            this.store.set('id_token', authResult.idToken);

            this.lock.getProfile(authResult.idToken, (error, profile) => {
                if (error) {
                    return;
                }
                this.store.set('profile', profile);
            });

            this.isLoggedIn = true;
            this.lock.hide();
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
    }

    app.service('authService', AuthService);
};
