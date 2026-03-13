import React, { useState } from 'react';

const FlashSaleItem = () => {
  const [product, setProduct] = useState({
    id: "P123",
    name: "Tai nghe Không dây",
    originalPrice: "1,000,000đ",
    salePrice: "500,000đ",
    stock: 500
  });
  
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);

  const handleBuy = async () => {
    setLoading(true);
    setMessage(null);
    try {
      const response = await fetch('http://localhost:8080/api/flash-sale/order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId: product.id, userId: "U_" + Math.floor(Math.random() * 10000), quantity: 1 })
      });
      const data = await response.json();
      
      if (response.ok && data.success) {
        setMessage({ type: 'success', text: 'Chúc mừng! Bạn đặt mua thành công.' });
        setProduct(prev => ({ ...prev, stock: prev.stock - 1 }));
      } else {
        setMessage({ type: 'error', text: data.message });
      }
    } catch (error) {
      setMessage({ type: 'error', text: 'Hệ thống đang nghẽn. Thử lại ngay.' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-sm mx-auto bg-white p-5 border rounded-lg shadow-lg">
      <div className="bg-red-500 text-white font-bold inline-block px-2 py-1 rounded mb-3">🔥 FLASH SALE 50%</div>
      <h2 className="text-xl font-semibold">{product.name}</h2>
      <div className="mt-2 text-gray-500 line-through">{product.originalPrice}</div>
      <div className="text-2xl text-red-600 font-bold mb-2">{product.salePrice}</div>
      <div className="text-sm font-medium mb-4">Kho còn: <span className="text-red-500">{product.stock}</span> | Max 2sp/người</div>
      
      {message && (
        <div className={`p-2 mb-4 rounded ${message.type === 'success' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
          {message.text}
        </div>
      )}

      <button 
        disabled={loading || product.stock <= 0}
        onClick={handleBuy}
        className={`w-full py-2 rounded text-white font-bold transition-all ${loading ? 'bg-gray-400' : product.stock > 0 ? 'bg-red-600 hover:bg-red-700 flex justify-center items-center' : 'bg-gray-400 cursor-not-allowed'}`}
      >
        {loading ? 'Đang xử lý...' : product.stock > 0 ? 'MUA NGAY' : 'HẾT HÀNG'}
      </button>
    </div>
  );
};

export default FlashSaleItem;
