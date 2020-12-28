import { Cache } from './cache'

export class NullCache implements Cache {
  get (key: string): Promise<Blob> {
    return new Promise((resolve, reject) => {
      reject(new Error('Null DB does not cache.'))
    })
  }

  public set (key: string, value: Blob): Promise<Blob> {
    return new Promise((resolve) => {
      console.info('Null DB..')
      resolve(value)
    })
  }

  protected remove (key: string) {
    console.info("Ignoring remove for '%s'", key)
  }
}
