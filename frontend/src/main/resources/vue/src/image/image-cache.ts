import { ICache } from './cache'

const dbName = 'zCache'
const imageStore = 'imageStore'

export class ImageCache implements ICache {
    private db!: IDBDatabase;

    initialize (): Promise<any> {
      return new Promise((resolve, reject) => {
        const request = indexedDB.open(dbName)
        request.onupgradeneeded = function () {
          console.log("Creating store '%s'.", imageStore)
          request.result.createObjectStore(imageStore)
        }
        request.onsuccess = () => {
          this.db = request.result
          resolve(this)
        }
        request.onerror = () => reject(request.error)
        request.onblocked = () => reject(request.error)
      })
    }

    get (key: string): Promise<any> {
      return new Promise((resolve, reject) => {
        const request = this.db.transaction(imageStore)
          .objectStore(imageStore)
          .get(key)
        request.onsuccess = () => {
          if (!request.result) {
            reject('No result found.')
          }
          resolve(request.result)
        }
        request.onerror = (error) => {
          reject(error)
        }
      })
    }

    public set (key: string, value: any): Promise<any> {
      return new Promise((resolve, reject) => {
        const request = this.db.transaction(imageStore, 'readwrite')
          .objectStore(imageStore)
          .add(value, key)
        request.onsuccess = () => {
          console.info("Cached image '%s'.", key)
          resolve(value)
        }
        request.onerror = (error) => {
          reject(error)
        }
      })
    }

    protected remove (key: string) {
      console.info("Ignoring remove for '%s'", key)
    }
}

export const imageCache = new ImageCache()
