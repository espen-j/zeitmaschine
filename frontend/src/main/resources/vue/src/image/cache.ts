export interface Cache {
    get(key: string): Promise<Blob>;
    set(key: string, value: Blob): Promise<Blob>;
}
