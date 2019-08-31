<template>
    <div class="image-overlay">
        <nav>
            <div class="close-button" v-on:click="close()">X</div>
        </nav>
        <div class="images">
            <div class="image" v-for="slide in slides">
                <img :src="slide.src" :ref="slide.image.name" v-next="loadNext" :data-index="slide.index">
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
            }
        }
    })
    export default class Slider extends Vue {

        @Prop()
        protected index!: number;
        private slides: ImageRendition[] = [];

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
        background-color: white;
        display: flex;
        flex-direction: column;

        nav {
            height: 50px;
            display: flex;
            flex-direction: column;

            .close-button {
                align-self: flex-end;
                cursor: pointer;
                width: 35px;
                height: 35px;
                font-size: 35px;
            }
        }

        // https://css-tricks.com/can-get-pretty-far-making-slider-just-html-css/
        .images {
            overflow-scrolling: touch;
            scroll-snap-type: x mandatory;
            display: flex;
            overflow-x: auto;
            overflow-y: hidden;

            scrollbar-width: none; /* Firefox */
            &::-webkit-scrollbar { /* WebKit */
                width: 0;
                height: 0;
            }

            .image {
                scroll-snap-align: start;
                text-align: center;
                flex: 0 0 100%;
                height:100%;
                white-space: nowrap;

                // https://stackoverflow.com/questions/7273338/how-to-vertically-align-an-image-inside-a-div
                &:before {
                    content: "";
                    display: inline-block;
                    height: 100%;
                    vertical-align: middle;
                }

                img {
                    max-height: 100%;
                    max-width: 100vw;
                    object-fit: scale-down;
                    // https://css-tricks.com/almanac/properties/t/touch-action/
                    touch-action: pinch-zoom;
                    vertical-align: middle;

                }
            }
        }
    }
</style>