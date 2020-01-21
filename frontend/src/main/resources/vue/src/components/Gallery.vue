<template>
    <div class="container">
        <div class="cell" v-for="image in images">
            <img src="" v-lazyload :data-image="image.name" v-on:click="open(image)"/>
        </div>
    </div>
</template>

<script lang="ts">
    import {Component, Vue} from 'vue-property-decorator';
    import {Image} from '../image/image';
    import {imageService} from '../image/image-service';
    import debounce from 'lodash.debounce';
    import throttle from 'lodash.throttle';
    import Slider from './Slider.vue';
    import router from "../router";

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

        protected created() {

            this.$store.dispatch('loadImages');

            this.registerScrollHandler();
        }

        protected open(image: Image) {
            router.push({ name: 'slide', hash: `#${image.name}`});
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

            // Hide border: https://stackoverflow.com/questions/10441362/how-can-i-remove-the-border-around-an-image-without-a-source
            // depends on empty src attribute in html.
            &[src=""] {
                visibility: hidden;
            }
            &[src="blob:*"] {
                visibility: visible;
            }
        }
    }

</style>
