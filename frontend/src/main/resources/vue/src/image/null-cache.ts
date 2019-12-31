import {ICache} from "./cache";

export class NullCache implements ICache {

    get(key: string): Promise<any> {
        return new Promise((resolve, reject) => {
            reject("Null DB does not cache.")
        })
    }

    public set(key: string, value: any): Promise<any> {
        return new Promise((resolve, reject) => {
            console.info("Null DB..");
            resolve(value);
        })
    }

    protected remove(key: string) {
        console.info("Ignoring remove for '%s'", key)
    }

}
