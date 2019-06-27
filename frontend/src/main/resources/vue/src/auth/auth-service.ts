import auth0, {Auth0DecodedHash, Auth0Error} from 'auth0-js';
import {EventEmitter} from 'events';
import axios from 'axios';

const webAuth = new auth0.WebAuth({
    domain: process.env.VUE_APP_AUTH0_DOMAIN,
    redirectUri: `${window.location.origin}/callback`,
    clientID: process.env.VUE_APP_AUTH0_CLIENT_ID,
    responseType: 'id_token',
    scope: 'openid profile email'
});

const localStorageKey = 'loggedIn';

class AuthService extends EventEmitter {
    private readonly domain: string;
    private clientId: string;


    constructor() {
        super();
        this.domain = process.env.VUE_APP_AUTH0_DOMAIN;
        this.clientId = process.env.VUE_APP_AUTH0_CLIENT_ID;
        console.log('creating new instance of auth-service for domain: ' + this.domain);
    }

    // Starts the user login flow
    public login() {
        webAuth.authorize();
    }

    // Handles the callback request from Auth0
    public handleAuthentication() {
        return new Promise((resolve, reject) => {
            webAuth.parseHash((error: Auth0Error, authResult: Auth0DecodedHash | null) => {
                if (error) {
                    console.log('error auth0');
                    reject(error);
                } else {
                    console.log('NOT error auth0');
                    if (authResult !== null) {
                        console.log('success auth0');
                        console.log(JSON.stringify(authResult));
                        localStorage.setItem(localStorageKey, 'true');

                        axios.interceptors.request.use(config => {

                            config.headers.Authorization = `Bearer ${authResult.idToken}`;
                            return config;
                        });

                        resolve();
                    }
                }
            });
        });
    }

    public isAuthenticated() {
        return (
            localStorage.getItem(localStorageKey) === 'true'
        );
    }

    reset() {
        localStorage.removeItem(localStorageKey);
    }
}
// Export a singleton instance in the global namespace
export const authService = new AuthService();
