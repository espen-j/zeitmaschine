const dbName: string = 'zCache';
const imageStore: string = 'imageStore';

class ImageCache {
    private db!: IDBDatabase;

    initialize(): Promise<any> {
        return new Promise((resolve, reject) => {
            let request = indexedDB.open(dbName);
            request.onupgradeneeded = function () {
                console.log("Creating store '%s'.", imageStore);
                request.result.createObjectStore(imageStore);
            };
            request.onsuccess = () => {
                this.db = request.result;
                resolve();
            };
            request.onerror = () => reject(request.error);
            request.onblocked = () => reject(request.error);
        })
    }

    get(key: string): Promise<any> {
        return new Promise((resolve, reject) => {

            let request = this.db.transaction(imageStore)
                .objectStore(imageStore)
                .get(key);
            request.onsuccess = () => {
                resolve(request.result)
            };
            request.onerror = (error) => {
                reject(error);
            }
        })
    }

    public set(key: string, value: any): Promise<any> {
        return new Promise((resolve, reject) => {

            let request = this.db.transaction(imageStore, "readwrite")
                .objectStore(imageStore)
                .add(value, key);
            request.onsuccess = () => {
                console.info("Cached image '%s'.", key);
                resolve(value);
            };
            request.onerror = (error) => {
                reject(error);
            }
        })
    }

    protected remove(key: string) {
        console.info("Ignoring remove for '%s'", key)
    }

}

export const imageCache = new ImageCache();
