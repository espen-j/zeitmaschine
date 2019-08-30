import Vue from 'vue'
import Vuex from 'vuex'
import { Image } from '../image/image';
import { imageService } from '../image/image-service';

Vue.use(Vuex);

export default new Vuex.Store({
    state: {
        images: [] as Image[]
    },
    mutations: {
        addImages(state, images: Image[]) {
            state.images.push(...images);
        }
    },
    actions: {
        loadImages({commit}) {
            imageService.getImages(this.state.images.length)
                .then(response => commit('addImages', response.data))
                .catch(reason => console.log('Failed', reason));
        }
    }
})