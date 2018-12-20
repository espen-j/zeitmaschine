import axios from 'axios';
import {Image} from './image';

const ELASTIC_ENDPOINT: string = 'http://localhost:9200/zeitmaschine/image/_search';
const PAGING_SIZE: number = 64;

class ImageService {

    constructor() {
        console.log('creating new instance of image-service');
    }

    public getImages(from: number = 0) {

        return axios.post<Image[]>(ELASTIC_ENDPOINT, {
                from,
                size : PAGING_SIZE,
                sort: [
                    { created : {order : 'desc'}}
                ]
            }, {
            transformResponse: data => {
                const json = JSON.parse(data);
                return (json.hits) ? this.transform(json.hits) : [];
            }
        });
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
