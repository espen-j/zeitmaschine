import axios from 'axios';
import {Image} from './image';

const PAGING_SIZE: number = 64;

class ImageService {

    private readonly endpoint: string;

    constructor() {
        this.endpoint = process.env.VUE_APP_ZM_ELASTIC_ENDPOINT;
        console.log('creating new instance of image-service with endpoint: ' + this.endpoint);
    }

    public getImages(from: number = 0) {

        return axios.post<Image[]>(this.endpoint, {
            from,
            size: PAGING_SIZE,
            sort: [
                {created: {order: 'desc'}}
            ]
        }, {
            transformResponse: data => {
                const json = JSON.parse(data);
                return (json.hits) ? this.transform(json.hits) : [];
            }
        });
    }

    public getImage(url: string) {
        return axios.request({url, responseType: 'blob'});
    }

    private transform(json: any): Image[] {
        return json.hits.map((hit: any) => {
            return {
                name: hit._source.name,
                thumbnail: hit._source.thumbnail
            };
        });
    }
}


// Export a singleton instance in the global namespace
export const imageService = new ImageService();
