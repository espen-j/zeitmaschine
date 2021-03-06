import axios from 'axios'
import { Image } from './image'
import { Cache } from './cache'
import { NullCache } from './null-cache'
import { ImageCache } from './image-cache'

const PAGING_SIZE = 64

class ImageService {
    private readonly endpoint: string;
    private imageCache: Cache = new NullCache();

    constructor () {
      this.endpoint = process.env.VUE_APP_ZM_ELASTIC_ENDPOINT
      console.log('creating new instance of image-service with endpoint: ' + this.endpoint)
      new ImageCache().initialize().then(cache => { this.imageCache = cache })
    }

    public getImages (from = 0) {
      return axios.post<Image[]>(this.endpoint, {
        from,
        size: PAGING_SIZE,
        sort: [
          { created: { order: 'desc' } }
        ]
      }, {
        transformResponse: data => {
          const json = JSON.parse(data)
          return (json.hits) ? this.transform(json.hits) : []
        }
      })
    }

    public getImage (name: string, rendition = 'thumbnail'): Promise<void | Blob> {
      const url = `image/${rendition}?name=${name}`
      return this.imageCache.get(url)
        .catch(() => {
          return axios.request({ url, responseType: 'blob' })
            .then(response => response.data)
            .then(data => {
              return this.imageCache.set(url, data)
                .catch(error => console.error("Error adding '%s' to cache: {}", url, error))
            })
        })
    }

    private transform (json: any): Image[] {
      return json.hits.map((hit: any) => {
        return {
          name: hit._source.name,
          thumbnail: hit._source.thumbnail,
          date: hit._source.created
        }
      })
    }
}

// Export a singleton instance in the global namespace
export const imageService = new ImageService()
