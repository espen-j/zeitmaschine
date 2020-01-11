<template>
    <div class="image-overlay">
        <nav>
            <i class="material-icons close-button" v-on:click="close()"></i>
            <p class="date">{{ date }}</p>
        </nav>
        <div class="images">
            <div class="image" v-for="slide in slides">
                <img :src="slide.src" :ref="slide.image.name" v-next="loadNext" v-select="displayDate" :data-index="slide.index" :data-date="slide.image.date">
            </div>
        </div>
    </div>
</template>

<script lang="ts">
    import {Component, Prop, Vue} from 'vue-property-decorator';
    import {Image} from '../image/image';
    import {imageService} from '../image/image-service';

    @Component({
        directives: {
            next: (el, binding) => {

                const callback = (entries: IntersectionObserverEntry[], observer: IntersectionObserver) => {
                    entries.forEach(entry => {
                        //console.info(entry);

                        if (entry.isIntersecting) {
                            observer.unobserve(el);
                            if (binding.value instanceof Function) {
                                let loadNext = binding.value;
                                let index = el.dataset.index;
                                loadNext(index);
                            }

                            // FIXME always called several times. Runs ones during registration of observer
                            // console.info(entry);
                        }
                    });
                };
                new IntersectionObserver(callback).observe(el);
            },
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
            }

        }
    })
    export default class Slider extends Vue {

        @Prop()
        protected index!: number;
        private slides: ImageRendition[] = [];

        private date!: string;

        protected close() {
            this.$emit('close');
        }

        protected created() {
            let current: Image = this.$store.state.images[this.index];
            imageService.getImage(current.name, 'small')
                .then(blob => URL.createObjectURL(blob))
                .then(src => this.slides.push({
                    image: current,
                    src: src,
                    index: this.index
                }))
                .catch(e => console.log(e));
        }

        loadNext(index: number) {
            index++;
            let slide = this.slides.find(slide => slide.index === index);
            if (slide) {
                console.log("ignoring ", index);
                return;
            }
            let next: Image = this.$store.state.images[index];

            imageService.getImage(next.name, 'small')
                .then(blob => URL.createObjectURL(blob))
                .then(src => this.slides.push({
                    image: next,
                    src: src,
                    index: index
                }))
                .then()
                .catch(e => console.log(e));
        }

        displayDate(date: string) {
            console.info(date);
            let options: Intl.DateTimeFormatOptions = {
                day: "numeric", month: "short", year: "numeric",
                hour: "2-digit", minute: "2-digit"
            };
            this.date = new Date(date).toLocaleDateString("en-GB", options);
        }
    }

    interface ImageRendition {
        image: Image
        src: string
        index: number
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