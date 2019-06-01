<template>
    <div class="container">
        <div class="cell" v-for="image in images">
            <img :src="'image/thumbnail?name=' + image.name" v-on:click="open(image)">
        </div>
        <span></span>
        <Slider v-if="sliderVisible" @close="closeSlider" :image="selected"/>
    </div>
</template>

<script lang="ts">
    import {Component, Prop, Vue} from 'vue-property-decorator';
    import {Image} from '../image/image';
    import {imageService} from '../image/image-service';
    import debounce from 'lodash.debounce';
    import throttle from 'lodash.throttle';
    import Slider from './Slider.vue';

    @Component({
        components: {
            Slider
        }
    })
    export default class Gallery extends Vue {

        private images: Image[] = [];
        private selected?: Image;
        private sliderVisible: boolean = false;

        protected created() {
            imageService.getImages()
                .then(response => this.images = response.data)
                .catch(reason => console.log('Failed', reason));

            this.registerScrollHandler();
        }

        protected open(image: Image) {
            this.selected = image;
            this.sliderVisible = true;
            console.info(image);
        }

        protected closeSlider() {
            this.selected = undefined;
            this.sliderVisible = false;
        }

        private registerScrollHandler() {
            window.addEventListener('scroll', throttle(() => {
                const scrollTop = document.documentElement.scrollTop;
                const innerHeight = window.innerHeight;
                const offsetHeight = document.documentElement.offsetHeight;

                const bottomOfWindow = scrollTop + innerHeight + 50 > offsetHeight;

                if (bottomOfWindow) {
                    console.log('bottom');
                    // create debounced function and call it in one line
                    debounce(this.load, 3000, {leading: true})();
                }
            }, 400));
        }

        private load() {
            console.log('loading.. ');
            imageService.getImages(this.images.length)
                .then(response => this.images.push(...response.data))
                .catch(reason => console.log('Failed', reason));
        }
    }

</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style lang="scss">
    .container {
        display: flex;
        flex-wrap: wrap;
    }

    .cell {
        width: 25vw;
        height: 25vw;
        flex: 0 1 auto;

        img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }
    }

</style>
