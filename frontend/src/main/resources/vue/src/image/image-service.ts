import axios from 'axios';

import { Image } from './image';

class ImageService {
    constructor() {
        console.log('creating new instance of image-service');
    }

    public getImages() {
        return axios.get<Image[]>(`/image`);
    }
}

// Export a singleton instance in the global namespace
export const imageService = new ImageService();
