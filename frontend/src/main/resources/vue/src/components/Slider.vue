<template>
    <div class="slider">
        <span class="close-button" v-on:click="close()">X</span>
        <img :src="src">
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
        }

        img {
            object-fit: scale-down;
        }
    }
</style>