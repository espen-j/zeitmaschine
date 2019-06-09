import auth0, {Auth0DecodedHash, Auth0Error} from 'auth0-js';
import {EventEmitter} from 'events';

const webAuth = new auth0.WebAuth({
    domain: process.env.VUE_APP_AUTH0_DOMAIN,
    redirectUri: `${window.location.origin}/callback`,
    clientID: process.env.VUE_APP_AUTH0_CLIENT_ID,
    responseType: 'id_token',
    scope: 'openid profile email'
});

const localStorageKey = 'loggedIn';
const loginEvent = 'loginEvent';


class AuthService extends EventEmitter {
    private readonly domain: string;
    private clientId: string;

    private idToken?: string;
    private tokenExpiry: number = Date.now();


    constructor() {
        super();
        this.domain = process.env.VUE_APP_AUTH0_DOMAIN;
        this.clientId = process.env.VUE_APP_AUTH0_CLIENT_ID;
        console.log('creating new instance of auth-service for domain: ' + this.domain);
    }

    // Starts the user login flow
    public login() {
        webAuth.authorize({});
    }

    // Handles the callback request from Auth0
    private handleAuthentication() {
        return new Promise((resolve, reject) => {
            webAuth.parseHash((error: Auth0Error, authResult: Auth0DecodedHash | null) => {
                if (error) {
                    reject(error);
                } else {
                    if (authResult !== null) {
                        this.localLogin(authResult);
                        resolve(authResult.idToken);
                    }

                }
            });
        });
    }

    private localLogin(authResult: Auth0DecodedHash) {
        this.idToken = authResult.idToken;
        const profile = authResult.idTokenPayload;

        // Convert the JWT expiry time from seconds to milliseconds
        this.tokenExpiry = new Date(profile.exp * 1000).getTime();

        localStorage.setItem(localStorageKey, 'true');

        this.emit(loginEvent, {
            loggedIn: true,
            profile: authResult.idTokenPayload,
            state: authResult.appState || {}
        });
    }

    private renewTokens() {
        return new Promise((resolve, reject) => {
            if (localStorage.getItem(localStorageKey) !== 'true') {
                return reject('Not logged in');
            }

            webAuth.checkSession({}, (err, authResult) => {
                if (err) {
                    reject(err);
                } else {
                    this.localLogin(authResult);
                    resolve(authResult);
                }
            });
        });
    }

    private logOut() {
        localStorage.removeItem(localStorageKey);

        this.tokenExpiry = Date.now();

        webAuth.logout({
            returnTo: window.location.origin
        });

        this.emit(loginEvent, { loggedIn: false });
    }

    private isAuthenticated() {
        return (
            Date.now() < this.tokenExpiry && localStorage.getItem(localStorageKey) === 'true'
        );
    }

}
// Export a singleton instance in the global namespace
export const authService = new AuthService();
