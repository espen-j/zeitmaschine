<template>
    <div class="image-overlay">
        <nav>
            <div class="close-button" v-on:click="close()">X</div>
        </nav>
        <div class="images">
            <div class="image">
                <span v-next></span><span v-prev></span>
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

    .image-overlay {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        right: 0;
        background-color: white;
        display: flex;
        flex-direction: column;
        //flex: 0 0 auto;

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
            scrollbar-width: none; /* Firefox */

            &::-webkit-scrollbar { /* WebKit */
            width: 0;
            height: 0;
        }

            .image {
                flex: 0 0 auto;
                scroll-snap-align: start;
                width: 100vw;

                img {
                    display:block;
                    margin:auto;
                    height: 100%;
                    object-fit: scale-down;
                }
            }
        }
    }
</style>