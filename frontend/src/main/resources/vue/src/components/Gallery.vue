<template>
    <div class="container">
        <div class="cell" v-for="image in images">
            <img v-lazyload :data-image="image.name" v-on:click="open(image)"/>
        </div>
        <span></span>
        <Slider v-if="sliderVisible" @close="closeSlider" :image="selected"/>
    </div>
</template>

<script lang="ts">
    import {Component, Vue} from 'vue-property-decorator';
    import {Image} from '../image/image';
    import {imageService} from '../image/image-service';
    import debounce from 'lodash.debounce';
    import throttle from 'lodash.throttle';
    import Slider from './Slider.vue';

    @Component({
        components: {
            Slider
        },
        directives: {
            lazyload: el => {

                function loadImage() {
                    if (el instanceof HTMLImageElement && el.dataset.image) {
                        imageService.getImage(el.dataset.image)
                            .then(blob => URL.createObjectURL(blob))
                            .then(src => el.src = src)
                            .catch(e => console.log(e));
                    }
                }

                const callback = (entries: IntersectionObserverEntry[], observer: IntersectionObserver) => {
                    entries.forEach(entry => {
                        if (entry.isIntersecting) {
                            loadImage();
                            observer.unobserve(el);
                        }
                    });
                };
                new IntersectionObserver(callback).observe(el);
            }
        }
    })
    export default class Gallery extends Vue {

        private selected?: Image;
        private sliderVisible: boolean = false;

        protected created() {

            this.$store.dispatch('loadImages');

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
            this.$store.dispatch('loadImages');
        }

        get images(): Image[] {
            return this.$store.state.images;
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
