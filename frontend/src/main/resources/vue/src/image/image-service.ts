import axios from 'axios';
import {Image} from './image';

const ELASTIC_ENDPOINT: string = 'http://localhost:9200/zeitmaschine/image/_search';

class ImageService {
    constructor() {
        console.log('creating new instance of image-service');
    }

    public getImages() {
        return axios.get<Image[]>(ELASTIC_ENDPOINT, {
            transformResponse: data => {
                return this.transform(data);
            }
        });
    }

    private transform(response: string): Image[] {
        return JSON.parse(response).hits.hits.map((hit: any) => {
            return {
                name: hit._source.name,
                thumbnail: hit._source.thumbnail
            };
        });
    }
}


// Export a singleton instance in the global namespace
export const imageService = new ImageService();
