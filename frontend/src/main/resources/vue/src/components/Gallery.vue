<template>
    <div class="container">
        <div class="cell" v-for="image in images">
            <img :src="image.thumbnail">
        </div>
    </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator';
import {Image} from '../image/image';
import {imageService} from '../image/image-service';

@Component
export default class Gallery extends Vue {

  private images: Image[] = [];

  protected created() {
    console.log('Created');
    imageService.getImages()
            .then(response => this.images = response.data)
            .catch(reason => console.log('Failed', reason));
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style >
  .container {
    margin: 0 auto;
    max-width: 1200px;
    padding: 0 1rem;
  }

  .cell {
      width: 150px;
      height: 150px;
      float: left;
  }

  .cell img {
      width: 100%;
      height: 100%;
      object-fit: cover;
  }

</style>
