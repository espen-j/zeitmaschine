import { InjectionKey } from 'vue'
import { createStore, Store } from 'vuex'
import { Image } from '../image/image'
import { imageService } from '../image/image-service'

// define your typings for the store state
export interface State {
  images: Image[];
}

// define injection key
export const key: InjectionKey<Store<State>> = Symbol('Injection key for the store.')

export const store: Store<State> = createStore<State>({
  state: {
    images: []
  },
  mutations: {
    addImages (state, images: Image[]) {
      state.images.push(...images)
    }
  },
  actions: {
    loadImages ({ commit }) {
      imageService.getImages(this.state.images.length)
        .then(response => commit('addImages', response.data))
        .catch(reason => console.log('Failed', reason))
    }
  }
})
