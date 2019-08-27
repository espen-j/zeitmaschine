<template>
    <div class="slider">
        <span class="close-button" v-on:click="close()">X</span>
        <div class="images">
            <div class="image">
                <img ref="image" :src="src">
            </div>
            <div class="image">
                <img ref="image" :src="src">
            </div>
            <div class="image">
                <img ref="image" :src="src">
            </div>
        </div>
    </div>
</template>

<script lang="ts">
    import {Component, Prop, Vue} from 'vue-property-decorator';
    import {Image} from '../image/image';
    import {imageService} from '../image/image-service';

    @Component
    export default class Slider extends Vue {

        @Prop()
        protected image!: Image;
        private src: string = '';

        protected close() {
            this.$emit('close');
        }

        protected created() {
            return imageService.getImage(this.image.name, 'small')
                .then(blob => URL.createObjectURL(blob))
                .then(src => this.src = src)
                .catch(e => console.log(e));
        }
    }
</script>

<style lang="scss">

    .slider {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        right: 0;
        background-color: white;
        display: flex;
        justify-content: center;
        align-items: center;

        .close-button {
            position: absolute;
            right: 30px;
            top: 30px;
            cursor: pointer;
            z-index: 100;
        }

        // https://css-tricks.com/can-get-pretty-far-making-slider-just-html-css/
        .images {
            overflow-scrolling: touch;
            scroll-snap-type: x mandatory;
            display: flex;
            overflow-x: auto;
            .image {
                flex: 0 0 auto;
                scroll-snap-align: start;
                img {
                    object-fit: scale-down;
                    width: 100vw;
                }
            }
        }
    }
</style>