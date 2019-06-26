<template>
    <figure v-lazyload class="image-lazy-wrapper">
        <img :src="'image/thumbnail?name=' + image.name" v-on:click="open(image)">
    </figure>
</template>

<script lang="ts">
    import {Component, Prop, Vue} from "vue-property-decorator";
    import {Image} from "../image/image";


    @Component({
        directives: {
            lazy: function (el) {


                function loadImage() {
                    let imageEl = Array.from(el.children).find(el => el.nodeName === 'IMG');
                    if (imageEl) {
                        imageEl.src = imageEl.dataset.url
                    }
                }

                let newVar = (entries, observer) => {
                    entries.forEach((entry) => {
                        if (entry.isIntersecting) {
                            loadImage();
                            observer.unobserve(el);
                        }
                    })
                };
                new IntersectionObserver(newVar).observe(el)


            }
        }
    })
    export default class LazyImage extends Vue {

        @Prop()
        protected image!: Image;

    }
</script>