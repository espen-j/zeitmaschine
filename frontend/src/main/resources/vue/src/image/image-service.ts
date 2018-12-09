import axios from 'axios';

import { Image } from './image';

const api = 'api';

class ImageService {
    constructor() {
        console.log('creating new instance of image-service');
    }

    public getImages() {
        return axios.get<Image[]>(`${api}/images`);
    }
}

// Export a singleton instance in the global namespace
export const imageService = new ImageService();
