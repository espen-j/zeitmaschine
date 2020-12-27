<template>
    <div class="image-overlay">
        <nav>
            <i class="material-icons close-button" v-on:click="close()"></i>
            <p class="date">{{ date }}</p>
        </nav>
        <div class="images">
            <div class="image" v-for="slide in slides" :id="slide.name">
                <img v-lazyload v-select="displayDate" src="" :data-date="slide.date" :data-image="slide.name">
            </div>
        </div>
    </div>
</template>

<script lang="ts">
    import {Options, Vue} from 'vue-class-component';
    import Prop from 'vue';

    import {Image} from '../image/image';
    import {imageService} from '../image/image-service';
    import router from "../router";

    @Options({
        directives: {
            select: (el, binding) => {

                const callback = (entries: IntersectionObserverEntry[], observer: IntersectionObserver) => {
                    entries.forEach(entry => {

                        if (entry.intersectionRatio == 1) {
                            let displayDate = binding.value;
                            let date = el.dataset.date;
                            displayDate(date);
                        }
                    });
                };
                new IntersectionObserver(callback, {threshold: 1.0}).observe(el);
            },
            lazyload: el => {

                function loadImage() {
                    if (el instanceof HTMLImageElement && el.dataset.image) {
                        imageService.getImage(el.dataset.image, 'small')
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
    export default class Slider extends Vue {

        @Prop()
        protected index!: number;

        private dateFormatted: string = "";

        protected close() {
            router.back();
        }

        created() {
            // Started with "scroll to anchor" from https://router.vuejs.org/guide/advanced/scroll-behavior.html
            // but this seems to need the DOM with the anchors to be in place. In our case those are added by this component.
            // Ended up with this via:
            // https://stackoverflow.com/questions/45201014/how-to-handle-anchors-bookmarks-with-vue-router/45206192?noredirect=1#comment84019465_45206192

            setTimeout(() => location.hash = this.$route.hash, 1);
        }

        displayDate(date: string) {
            console.info(date);
            let options: Intl.DateTimeFormatOptions = {
                day: "numeric", month: "short", year: "numeric",
                hour: "2-digit", minute: "2-digit"
            };
            this.dateFormatted = new Date(date).toLocaleDateString("en-GB", options);
        }

        get date(): string {
            return this.dateFormatted;
        }

        get slides(): Image[] {
            return this.$store.state.images;
        }
    }

</script>

<style lang="scss">

    .image-overlay {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        right: 0;
        display: flex;
        flex-direction: column;
        background-color: black;

        nav {
            height: 50px;
            display: flex;
            flex-direction: row;

            .close-button {
                align-self: flex-start;
                cursor: pointer;
                font-size: 50px;

                &:before {
                    content: "chevron_left";
                }
            }
        }

        // https://css-tricks.com/can-get-pretty-far-making-slider-just-html-css/
        .images {
            overflow-scrolling: touch;

            // ios fix
            -webkit-overflow-scrolling: touch;

            scroll-snap-type: x mandatory;
            display: flex;
            overflow-x: auto;
            overflow-y: hidden;
            height: 100%;

            scrollbar-width: none; /* Firefox */
            &::-webkit-scrollbar { /* WebKit */
                width: 0;
                height: 0;
            }

            .image {
                scroll-snap-align: start;

                // stretch all elements to fill the width
                flex: 0 0 100%;

                // vertically align image
                display: flex;
                align-items: center;
                justify-content: center;

                img {
                    max-height: 100%;
                    max-width: 100vw;
                    object-fit: scale-down;
                }
            }
        }
    }
</style>
